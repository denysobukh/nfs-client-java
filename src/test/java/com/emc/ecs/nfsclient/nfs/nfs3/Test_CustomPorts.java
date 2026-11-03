package com.emc.ecs.nfsclient.nfs.nfs3;

import static org.junit.Assert.*;

import com.emc.ecs.nfsclient.nfs.Nfs;
import com.emc.ecs.nfsclient.rpc.Credential;
import com.emc.ecs.nfsclient.rpc.CredentialUnix;
import com.emc.ecs.nfsclient.rpc.RpcResponse;
import com.emc.ecs.nfsclient.rpc.Xdr;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;

public class Test_CustomPorts {
  private static final byte[] ROOT = {1, 2, 3, 4};
  private static final Credential CREDENTIAL = new CredentialUnix(123, 456, null);

  @Test(timeout = 15000)
  public void explicitPortsRouteMountUnmountAndNfsCallsWithoutDiscovery() throws Exception {
    try (Peer mount = new Peer(Nfs.MOUNTPROG, 1, 3, 1, 3);
        Peer nfs = new Peer(Nfs.RPC_PROGRAM, 0, 3)) {
      Nfs3 client = new Nfs3("127.0.0.1", "/export", CREDENTIAL, 1, nfs.port(), mount.port());
      Nfs<?> api = client;
      assertEquals(nfs.port(), api.getPort());
      assertEquals(mount.port(), api.getMountPort());
      assertSame(CREDENTIAL, api.getCredential());
      assertArrayEquals(ROOT, api.getRootFileHandle());
      new RpcResponse().unmarshalling(api.nullCall());
      assertArrayEquals(ROOT, api.getLookup(api.makeLookupRequest(ROOT, "child")).getFileHandle());
      // A subsequent mount must continue to use the explicitly configured MOUNT port.
      assertArrayEquals(ROOT, client.lookupRootHandle());
      mount.await();
      nfs.await();
    }
  }

  @Test(timeout = 15000)
  public void suppliedHandleAndExplicitNfsPortNeedNoDiscoveryOrMount() throws Exception {
    try (Peer nfs = new Peer(Nfs.RPC_PROGRAM, 0)) {
      byte[] handle = ROOT.clone();
      Nfs3 client = new Nfs3("127.0.0.1", "/export", handle, null, 1, nfs.port(), 0);
      handle[0] = 99;
      assertArrayEquals(ROOT, client.getRootFileHandle());
      byte[] copy = client.getRootFileHandle();
      copy[0] = 99;
      assertArrayEquals(ROOT, client.getRootFileHandle());
      assertTrue(client.getCredential() instanceof CredentialUnix);
      assertEquals(0, client.getMountPort());
      new RpcResponse().unmarshalling(client.nullCall());
      nfs.await();
    }
    // Construction with a handle and an IP literal needs no server or portmapper.
    Nfs3 offline = new Nfs3("127.0.0.1", "/export", ROOT, null, 1, 65535, 1);
    assertEquals(65535, offline.getPort());
    assertEquals(1, offline.getMountPort());
  }

  @Test(timeout = 15000)
  public void zeroDiscoversOnlyTheUnspecifiedService() throws Exception {
    try (Peer mount = new Peer(Nfs.MOUNTPROG, 1, 3);
        Peer nfs = new Peer(Nfs.RPC_PROGRAM, 0)) {
      resetDiscovery();
      Nfs3 client =
          new DiscoveryClient("127.0.0.1", export(nfs, mount), CREDENTIAL, 1, nfs.port(), 0);
      assertEquals(0, DiscoveryClient.nfsQueries.get());
      assertEquals(1, DiscoveryClient.mountQueries.get());
      assertEquals(mount.port(), client.getMountPort());
      new RpcResponse().unmarshalling(client.nullCall());
      mount.await();
      nfs.await();
    }
    try (Peer mount = new Peer(Nfs.MOUNTPROG, 1, 3);
        Peer nfs = new Peer(Nfs.RPC_PROGRAM, 0)) {
      resetDiscovery();
      Nfs3 client =
          new DiscoveryClient("127.0.0.1", export(nfs, mount), CREDENTIAL, 1, 0, mount.port());
      assertEquals(1, DiscoveryClient.nfsQueries.get());
      assertEquals(0, DiscoveryClient.mountQueries.get());
      assertEquals(nfs.port(), client.getPort());
      new RpcResponse().unmarshalling(client.nullCall());
      mount.await();
      nfs.await();
    }
  }

  @Test(timeout = 15000)
  public void everyLegacyConstructorKeepsAutomaticDiscoveryAndHandleBehavior() throws Exception {
    for (int variant = 0; variant < 5; variant++) {
      boolean hasHandle = variant == 0 || variant == 4;
      try (Peer mount = new Peer(Nfs.MOUNTPROG, hasHandle ? new int[0] : new int[] {1, 3});
          Peer nfs = new Peer(Nfs.RPC_PROGRAM, 0)) {
        resetDiscovery();
        String path = export(nfs, mount);
        Nfs3 client;
        switch (variant) {
          case 0:
            client = new DiscoveryClient("127.0.0.1", path, ROOT, 1);
            break;
          case 1:
            client = new DiscoveryClient("127.0.0.1", path, CREDENTIAL, 1);
            break;
          case 2:
            client = new DiscoveryClient("127.0.0.1:" + path, 123, 456, 1);
            break;
          case 3:
            client = new DiscoveryClient("127.0.0.1:" + path, CREDENTIAL, 1);
            break;
          default:
            client = new DiscoveryClient("127.0.0.1", path, ROOT, CREDENTIAL, 1);
        }
        assertEquals(1, DiscoveryClient.nfsQueries.get());
        assertEquals(hasHandle ? 0 : 1, DiscoveryClient.mountQueries.get());
        assertEquals(hasHandle ? 0 : mount.port(), client.getMountPort());
        assertEquals(nfs.port(), client.getPort());
        assertArrayEquals(ROOT, client.getRootFileHandle());
        assertEquals("127.0.0.1", client.getServer());
        assertEquals(path, client.getExportedPath());
        new RpcResponse().unmarshalling(client.nullCall());
        mount.await();
        nfs.await();
      }
    }
  }

  @Test
  public void invalidPortsFailBeforeNetworkAccessEvenWithSuppliedHandle() {
    for (int invalid : new int[] {-1, 65536, Integer.MIN_VALUE, Integer.MAX_VALUE}) {
      IllegalArgumentException nfsError =
          assertThrows(
              IllegalArgumentException.class,
              () -> new Nfs3("unused.invalid", "/export", ROOT, null, 1, invalid, 1));
      assertTrue(nfsError.getMessage().contains("nfsPort"));
      IllegalArgumentException mountError =
          assertThrows(
              IllegalArgumentException.class,
              () -> new Nfs3("unused.invalid", "/export", ROOT, null, 1, 1, invalid));
      assertTrue(mountError.getMessage().contains("mountPort"));
    }
  }

  private static void resetDiscovery() {
    DiscoveryClient.nfsQueries.set(0);
    DiscoveryClient.mountQueries.set(0);
  }

  private static String export(Peer nfs, Peer mount) {
    return "/" + nfs.port() + "/" + mount.port();
  }

  /**
   * Preserve the old package discovery hooks; encode endpoints in the export for constructor use.
   */
  private static class DiscoveryClient extends Nfs3 {
    static final AtomicInteger nfsQueries = new AtomicInteger();
    static final AtomicInteger mountQueries = new AtomicInteger();

    DiscoveryClient(String s, String p, byte[] h, int r) throws IOException {
      super(s, p, h, r);
    }

    DiscoveryClient(String s, String p, Credential c, int r) throws IOException {
      super(s, p, c, r);
    }

    DiscoveryClient(String p, int u, int g, int r) throws IOException {
      super(p, u, g, r);
    }

    DiscoveryClient(String p, Credential c, int r) throws IOException {
      super(p, c, r);
    }

    DiscoveryClient(String s, String p, byte[] h, Credential c, int r) throws IOException {
      super(s, p, h, c, r);
    }

    DiscoveryClient(String s, String p, Credential c, int r, int n, int m) throws IOException {
      super(s, p, c, r, n, m);
    }

    @Override
    int getNfsPortFromServer() {
      nfsQueries.incrementAndGet();
      return Integer.parseInt(getExportedPath().split("/")[1]);
    }

    @Override
    int getMountPortFromServer() {
      mountQueries.incrementAndGet();
      return Integer.parseInt(getExportedPath().split("/")[2]);
    }
  }

  /** Real TCP RPC peer: verify destination service/procedure, return minimal successful replies. */
  private static final class Peer implements AutoCloseable {
    private final ServerSocket server = new ServerSocket(0, 10, InetAddress.getLoopbackAddress());
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Future<?> task;

    Peer(int program, int... procedures) throws IOException {
      server.setSoTimeout(5000);
      task =
          executor.submit(() -> {
            if (procedures.length == 0) return;
            try (Socket socket = server.accept()) {
              socket.setSoTimeout(5000);
              DataInputStream in = new DataInputStream(socket.getInputStream());
              DataOutputStream out = new DataOutputStream(socket.getOutputStream());
              for (int procedure : procedures) {
                int marker = in.readInt();
                assertTrue((marker & 0x80000000) != 0);
                byte[] request = new byte[marker & 0x7fffffff];
                in.readFully(request);
                ByteBuffer header = ByteBuffer.wrap(request);
                assertEquals(program, header.getInt(12));
                assertEquals(3, header.getInt(16));
                assertEquals(procedure, header.getInt(20));
                Xdr reply = new Xdr(128);
                reply.putInt(header.getInt(0));
                reply.putInt(1); // REPLY
                reply.putInt(0); // MSG_ACCEPTED
                reply.putInt(0); // AUTH_NONE verifier
                reply.putInt(0); // verifier length
                reply.putInt(0); // SUCCESS
                if (program == Nfs.MOUNTPROG && procedure == 1) {
                  reply.putInt(0); // MNT3_OK
                  reply.putByteArray(ROOT);
                  reply.putInt(1);
                  reply.putInt(1); // AUTH_SYS
                } else if (program == Nfs.RPC_PROGRAM && procedure == 3) {
                  reply.putInt(0); // NFS3_OK
                  reply.putByteArray(ROOT);
                  reply.putInt(0); // no object attributes
                  reply.putInt(0); // no directory attributes
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
