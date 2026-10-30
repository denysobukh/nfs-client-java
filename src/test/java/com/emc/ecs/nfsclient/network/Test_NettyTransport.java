package com.emc.ecs.nfsclient.network;

import com.emc.ecs.nfsclient.rpc.Xdr;
import com.emc.ecs.nfsclient.rpc.RpcException;
import com.emc.ecs.nfsclient.LogCapture;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.Test;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.*;
import static org.junit.Assert.*;

public class Test_NettyTransport {
    @Test
    public void fragmentedAndCoalescedRecords() {
        EmbeddedChannel channel = new EmbeddedChannel(new RPCRecordDecoder());
        ByteBuf wire = Unpooled.buffer();
        wire.writeInt(4).writeInt(123).writeInt(0x80000004).writeInt(456);
        wire.writeInt(0x80000004).writeInt(789);
        try {
            // Feed bytes individually, including incomplete headers and payloads.
            while (wire.isReadable()) {
                channel.writeInbound(wire.readRetainedSlice(1));
            }
            byte[] first = channel.readInbound();
            Xdr response = RecordMarkingUtil.removeRecordMarking(first);
            assertEquals(123, response.getXid());
            response.setOffset(4);
            assertEquals(456, response.getInt());
            byte[] second = channel.readInbound();
            assertEquals(789, RecordMarkingUtil.removeRecordMarking(second).getXid());
            assertNull(channel.readInbound());
            assertTrue(channel.writeInbound(Unpooled.wrappedBuffer(first, second)));
            assertArrayEquals(first, (byte[]) channel.readInbound());
            assertArrayEquals(second, (byte[]) channel.readInbound());
        } finally {
            wire.release();
            channel.finishAndReleaseAll();
        }
    }

    @Test(timeout = 15000)
    public void tcpRoundTripAndDisconnect() throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try (ServerSocket server = new ServerSocket(0)) {
            Future<?> peer = executor.submit(() -> {
                try (Socket socket = server.accept()) {
                    DataInputStream in = new DataInputStream(socket.getInputStream());
                    DataOutputStream out = new DataOutputStream(socket.getOutputStream());
                    assertEquals(0x80000008, in.readInt());
                    int xid = in.readInt();
                    assertEquals(42, in.readInt());
                    out.writeInt(0x80000008);
                    out.writeInt(xid);
                    out.writeInt(84);
                    out.flush();
                    // Close while a second request is pending.
                    in.readInt();
                    in.readInt();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
            Connection connection = new Connection("127.0.0.1", server.getLocalPort(), false);
            try {
                connection.connect();
                Xdr request = new Xdr(8);
                request.setXid(123);
                request.putInt(123);
                request.putInt(42);
                Xdr response = connection.sendAndWait(5, request);
                assertEquals(123, response.getXid());
                response.setOffset(4);
                assertEquals(84, response.getInt());
                try {
                    connection.sendAndWait(5, request);
                    fail("Expected network error after peer closes");
                } catch (RpcException expected) {
                    // Pending RPCs must be failed when the peer disconnects.
                }
                peer.get(5, TimeUnit.SECONDS);
            } finally {
                connection.close();
            }
        } finally {
            executor.shutdownNow();
        }
    }

    @Test(timeout = 15000)
    public void failedConnectUnblocksSender() throws Exception {
        int port;
        try (ServerSocket server = new ServerSocket(0)) {
            port = server.getLocalPort();
        }
        Connection connection = new Connection("127.0.0.1", port, false);
        try {
            connection.connect();
            try {
                connection.sendAndWait(1, new Xdr(4));
                fail("Expected failed connection");
            } catch (RpcException expected) {
                assertEquals(Connection.State.DISCONNECTED, connection.getConnectionState());
                assertNotNull(expected.getCause());
                assertTrue(expected.getCause() instanceof java.net.ConnectException);
                assertTrue(expected.getMessage().contains("remote=/127.0.0.1:" + port));
                assertTrue(expected.getMessage().contains("connectTimeoutMs=10000"));
            }
        } finally {
            connection.close();
        }
    }

    @Test(timeout = 15000)
    public void rpcTimeoutIdentifiesRequestAndEndpoint() throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        CountDownLatch releasePeer = new CountDownLatch(1);
        try (ServerSocket server = new ServerSocket(0)) {
            server.setSoTimeout(5000);
            Future<?> peer = executor.submit(() -> {
                try (Socket socket = server.accept()) {
                    socket.setSoTimeout(5000);
                    DataInputStream in = new DataInputStream(socket.getInputStream());
                    in.readInt();
                    in.readInt();
                    releasePeer.await(5, TimeUnit.SECONDS);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
            Connection connection = new Connection("127.0.0.1", server.getLocalPort(), false);
            try {
                connection.connect();
                Xdr request = new Xdr(4);
                request.setXid(321);
                request.putInt(321);
                RpcException error = assertThrows(RpcException.class, () -> connection.sendAndWait(1, request));
                assertTrue(error.getMessage().contains("RPC response timeout"));
                assertTrue(error.getMessage().contains("remote=/127.0.0.1:" + server.getLocalPort()));
                assertTrue(error.getMessage().contains("local="));
                assertTrue(error.getMessage().contains("xid=321"));
                assertTrue(error.getMessage().contains("timeoutSeconds=1"));
                assertNull(error.getCause());
                releasePeer.countDown();
                peer.get(5, TimeUnit.SECONDS);
            } finally {
                connection.close();
            }
        } finally {
            releasePeer.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    public void channelFailureIsLoggedAndPreservesOriginalThrowable() throws Exception {
        Connection connection = new Connection("127.0.0.1", 2049, false);
        EmbeddedChannel channel = new EmbeddedChannel(new ClientIOHandler(connection));
        Throwable cause = new IllegalStateException("malformed RPC record");
        try (LogCapture logs = new LogCapture()) {
            channel.pipeline().fireExceptionCaught(cause);
            RpcException error = assertThrows(RpcException.class, () -> connection.sendAndWait(1, new Xdr(4)));
            assertSame(cause, error.getCause());
            assertTrue(logs.output().contains("TCP channel failed"));
            assertTrue(logs.output().contains("malformed RPC record"));
            assertTrue(logs.output().contains("2049"));
        } finally {
            channel.finishAndReleaseAll();
        }
    }
}
