package com.emc.ecs.nfsclient.network;

import com.emc.ecs.nfsclient.rpc.Xdr;
import com.emc.ecs.nfsclient.rpc.RpcException;
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
            }
        } finally {
            connection.close();
        }
    }
}
