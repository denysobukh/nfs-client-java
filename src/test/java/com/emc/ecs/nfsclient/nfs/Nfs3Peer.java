package com.emc.ecs.nfsclient.nfs;

import static org.junit.Assert.*;

import com.emc.ecs.nfsclient.nfs.nfs3.Nfs3;
import com.emc.ecs.nfsclient.rpc.CredentialNone;
import java.io.*;
import java.net.*;
import java.nio.ByteBuffer;
import java.util.concurrent.*;

/** Loopback TCP RPC peer with bounded waits and failures propagated to the test thread. */
public final class Nfs3Peer implements AutoCloseable {
  @FunctionalInterface
  public interface Responder {
    byte[] reply(int procedure, ByteBuffer request);
  }

  private final ServerSocket server =
      new ServerSocket(0, 10, InetAddress.getByAddress(new byte[] {127, 0, 0, 1}));
  private final ExecutorService executor = Executors.newSingleThreadExecutor();
  private final Future<?> worker;
  private volatile Socket connection;
  private volatile boolean closing;

  public Nfs3Peer() throws IOException {
    this((p, r) -> Nfs3Wire.response(r.getInt(0), p, 0, true));
  }

  public Nfs3Peer(Responder responder) throws IOException {
    server.setSoTimeout(5000);
    worker =
        executor.submit(() -> {
          try (Socket socket = server.accept()) {
            connection = socket;
            socket.setSoTimeout(5000);
            DataInputStream input = new DataInputStream(socket.getInputStream());
            DataOutputStream output = new DataOutputStream(socket.getOutputStream());
            while (!closing) {
              int marker = input.readInt();
              assertTrue("single final RPC record fragment", marker < 0);
              int length = marker & 0x7fffffff;
              assertTrue(length >= 40 && length <= 1024 * 1024);
              byte[] record = new byte[length];
              input.readFully(record);
              ByteBuffer r = ByteBuffer.wrap(record);
              assertEquals(0, r.getInt(4));
              assertEquals(2, r.getInt(8));
              assertEquals(100003, r.getInt(12));
              assertEquals(3, r.getInt(16));
              byte[] reply = responder.reply(r.getInt(20), r);
              output.writeInt(0x80000000 | reply.length);
              output.write(reply);
              output.flush();
            }
          } catch (IOException e) {
            if (!closing) throw new UncheckedIOException(e);
          }
        });
  }

  public Nfs3 client() throws IOException {
    return new Nfs3(
        "127.0.0.1",
        "/export",
        Nfs3Wire.handle(),
        new CredentialNone(),
        1,
        server.getLocalPort(),
        0);
  }

  @Override
  public void close() throws IOException {
    closing = true;
    server.close();
    Socket socket = connection;
    if (socket != null) socket.close();
    executor.shutdownNow();
    try {
      worker.get(6, TimeUnit.SECONDS);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IOException(e);
    } catch (ExecutionException | TimeoutException e) {
      throw new IOException("RPC peer failed", e);
    }
  }
}
