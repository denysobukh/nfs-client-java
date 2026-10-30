package com.emc.ecs.nfsclient.nfs.nfs3;

import com.emc.ecs.nfsclient.LogCapture;
import com.emc.ecs.nfsclient.mount.MountException;
import com.emc.ecs.nfsclient.mount.MountStatus;
import com.emc.ecs.nfsclient.rpc.CredentialUnix;
import com.emc.ecs.nfsclient.rpc.RpcException;
import com.emc.ecs.nfsclient.rpc.Xdr;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.Test;
import static org.junit.Assert.*;

public class Test_MountDiagnostics {
    @Test(timeout = 15000)
    public void permissionDeniedExplainsExportAndLocalPortPermissions() throws Exception {
        try (Peer peer = new Peer(13, new int[] {0}, new int[] {1}); LogCapture logs = new LogCapture()) {
            MountException error = assertThrows(MountException.class, () -> new LocalNfs3(peer.port()));
            assertSame(MountStatus.MNT3ERR_ACCES, error.getStatus());
            assertContains(error.getMessage(), "server=127.0.0.1", "mountPort=" + peer.port(),
                    "export=/test-export-" + peer.port(), "nfsVersion=3", "attempt=1/2",
                    "privilegedPort=false", "MNT3ERR_ACCES(13)", "Permission denied", "allowlist",
                    "local OS permission", "AUTH_SYS UID 0 does not grant it");
            peer.await();
            assertContains(logs.output(), "WARN", "MOUNT failed", "MNT3ERR_ACCES(13)");
        }
    }

    @Test(timeout = 15000)
    public void missingExportHasPathGuidance() throws Exception {
        try (Peer peer = new Peer(2, new int[] {0}, new int[] {1})) {
            MountException error = assertThrows(MountException.class, () -> new LocalNfs3(peer.port()));
            assertSame(MountStatus.MNT3ERR_NOENT, error.getStatus());
            assertContains(error.getMessage(), "MNT3ERR_NOENT(2)", "exported path exists");
            peer.await();
        }
    }

    @Test(timeout = 15000)
    public void unknownMountStatusRetainsNumericValue() throws Exception {
        try (Peer peer = new Peer(54321, new int[] {0}, new int[] {1})) {
            MountException error = assertThrows(MountException.class, () -> new LocalNfs3(peer.port()));
            assertEquals(54321, error.getStatus().getValue());
            assertContains(error.getMessage(), "UNKNOWN_MOUNTSTATUS(54321)");
            assertFalse(error.getMessage().contains("Permission denied"));
            peer.await();
        }
    }

    @Test(timeout = 15000)
    public void unavailableMountProgramPreservesRpcCause() throws Exception {
        try (Peer peer = new Peer(0, new int[] {1}, new int[] {1})) {
            MountException error = assertThrows(MountException.class, () -> new LocalNfs3(peer.port()));
            assertTrue(error.getCause() instanceof RpcException);
            assertContains(error.getMessage(), "MOUNT RPC failed", "mountPort=" + peer.port(),
                    "PROG_UNAVAIL(1)", "xid=");
            peer.await();
        }
    }

    @Test(timeout = 15000)
    public void successfulMountAndCleanupAreEachSentOnce() throws Exception {
        try (Peer peer = new Peer(0, new int[] {0, 0}, new int[] {1, 3})) {
            Nfs3 client = new LocalNfs3(peer.port());
            assertArrayEquals(new byte[] {1, 2, 3, 4}, client.getRootFileHandle());
            peer.await();
        }
    }

    @Test(timeout = 15000)
    public void finalUnmountCleanupFailureIsLoggedWithoutDiscardingMount() throws Exception {
        try (Peer peer = new Peer(0, new int[] {0, 1, 1}, new int[] {1, 3, 3});
                LogCapture logs = new LogCapture()) {
            Nfs3 client = new LocalNfs3(peer.port());
            assertArrayEquals(new byte[] {1, 2, 3, 4}, client.getRootFileHandle());
            peer.await();
            assertContains(logs.output(), "UNMOUNT registration cleanup failed", "attempt=2/2",
                    "mount remains usable", "PROG_UNAVAIL(1)");
        }
    }

    private static void assertContains(String actual, String... expected) {
        for (String part : expected) {
            assertTrue("Missing '" + part + "' in: " + actual, actual.contains(part));
        }
    }

    /** Encodes the peer port in the export so it is available during superclass initialization. */
    private static final class LocalNfs3 extends Nfs3 {
        LocalNfs3(int port) throws IOException {
            super("127.0.0.1", "/test-export-" + port, new CredentialUnix(), 1);
        }

        @Override
        int getNfsPortFromServer() {
            return 2049;
        }

        @Override
        int getMountPortFromServer() {
            return Integer.parseInt(getExportedPath().substring("/test-export-".length()));
        }
    }

    /** A TCP peer that validates actual MOUNT/UNMOUNT requests and returns scripted RPC replies. */
    private static final class Peer implements AutoCloseable {
        private final ServerSocket server = new ServerSocket(0);
        private final ExecutorService executor = Executors.newSingleThreadExecutor();
        private final Future<?> task;

        Peer(int mountStatus, int[] acceptStatuses, int[] procedures) throws IOException {
            server.setSoTimeout(5000);
            task = executor.submit(() -> {
                try (Socket socket = server.accept()) {
                    socket.setSoTimeout(5000);
                    DataInputStream in = new DataInputStream(socket.getInputStream());
                    DataOutputStream out = new DataOutputStream(socket.getOutputStream());
                    for (int i = 0; i < acceptStatuses.length; ++i) {
                        int marker = in.readInt();
                        assertTrue((marker & 0x80000000) != 0);
                        byte[] request = new byte[marker & 0x7fffffff];
                        in.readFully(request);
                        ByteBuffer header = ByteBuffer.wrap(request);
                        assertEquals(100005, header.getInt(12));
                        assertEquals(3, header.getInt(16));
                        assertEquals(procedures[i], header.getInt(20));
                        Xdr reply = new Xdr(64);
                        reply.putInt(header.getInt(0));
                        reply.putInt(1); // REPLY
                        reply.putInt(0); // MSG_ACCEPTED
                        reply.putInt(0); // AUTH_NONE verifier
                        reply.putInt(0); // empty verifier
                        reply.putInt(acceptStatuses[i]);
                        if (acceptStatuses[i] == 0 && procedures[i] == 1) {
                            reply.putInt(mountStatus);
                            if (mountStatus == 0) {
                                reply.putByteArray(new byte[] {1, 2, 3, 4});
                                reply.putInt(1);
                                reply.putInt(1); // AUTH_SYS
                            }
                        }
                        out.writeInt(0x80000000 | reply.getOffset());
                        out.write(reply.getBuffer(), 0, reply.getOffset());
                        out.flush();
                    }
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
        }

        int port() {
            return server.getLocalPort();
        }

        void await() throws Exception {
            task.get(6, TimeUnit.SECONDS);
        }

        @Override
        public void close() throws IOException {
            server.close();
            executor.shutdownNow();
        }
    }
}
