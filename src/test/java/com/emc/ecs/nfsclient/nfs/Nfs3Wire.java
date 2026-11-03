package com.emc.ecs.nfsclient.nfs;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * Independent RFC 1813 / RFC 4506 wire fixtures; deliberately does not use the client XDR encoder.
 */
public final class Nfs3Wire {
  public static byte[] handle() {
    return new byte[] {1, 2, 3};
  }

  public static final long VERIFIER = 0x8123456789abcdefL;
  private final ByteBuffer bytes = ByteBuffer.allocate(8192);

  public Nfs3Wire ints(int... values) {
    for (int v : values) bytes.putInt(v);
    return this;
  }

  public Nfs3Wire hyper(long v) {
    bytes.putLong(v);
    return this;
  }

  public Nfs3Wire opaque(byte[] v) {
    ints(v.length);
    bytes.put(v);
    while (bytes.position() % 4 != 0) bytes.put((byte) 0);
    return this;
  }

  public Nfs3Wire string(String v) {
    return opaque(v.getBytes(StandardCharsets.UTF_8));
  }

  public Nfs3Wire attributes(int type, long size) {
    return ints(type, 0644, 2, -1, -2)
        .hyper(size)
        .hyper(4096)
        .ints(8, 9)
        .hyper(11)
        .hyper(12)
        .ints(10, 123000000, 20, 456000000, 30, 789000000);
  }

  public Nfs3Wire post(boolean present, int type, long size) {
    ints(present ? 1 : 0);
    if (present) attributes(type, size);
    return this;
  }

  public Nfs3Wire wcc(boolean present) {
    ints(present ? 1 : 0);
    if (present) hyper(7).ints(20, 456000000, 30, 789000000);
    return post(present, 1, 7);
  }

  public byte[] build() {
    return Arrays.copyOf(bytes.array(), bytes.position());
  }

  public static Nfs3Wire reply(int xid, int status) {
    return new Nfs3Wire().ints(xid, 1, 0, 0, 0, 0, status);
  }

  /** RFC 1813 sections 3.3.1-3.3.21: success/failure union arms and optional attributes. */
  public static byte[] response(int xid, int procedure, int status, boolean optional) {
    Nfs3Wire w = reply(xid, status);
    boolean ok = status == 0;
    switch (procedure) {
      case 1 -> {
        if (ok) w.attributes(1, 7);
      }
      case 2, 12, 13 -> w.wcc(optional);
      case 3 -> {
        if (ok) w.opaque(handle()).post(optional, 1, 7);
        w.post(optional, 2, 7);
      }
      case 4, 5, 6, 16, 17, 18, 19, 20 -> {
        w.post(optional, 1, 7);
        if (ok)
          switch (procedure) {
            case 4 -> w.ints(63);
            case 5 -> w.string("target");
            case 6 -> w.ints(3, 1).opaque(new byte[] {0, (byte) 255, 42});
            case 16, 17 -> {
              w.hyper(VERIFIER).ints(1).hyper(12).string("child").hyper(99);
              if (procedure == 17) {
                w.post(optional, 1, 7).ints(optional ? 1 : 0);
                if (optional) w.opaque(handle());
              }
              w.ints(0, 1);
            }
            case 18 ->
                w.hyper(1000).hyper(800).hyper(700).hyper(600).hyper(500).hyper(400).ints(-1);
            case 19 -> w.ints(4096, 2048, 4, 4096, 2048, 4, 1024).hyper(1L << 40).ints(0, 1000, 27);
            case 20 -> w.ints(32000, 255, 1, 1, 0, 1);
            default -> throw new AssertionError(procedure);
          }
      }
      case 7, 21 -> {
        w.wcc(optional);
        if (ok) {
          if (procedure == 7) w.ints(3, 2);
          w.hyper(VERIFIER);
        }
      }
      case 8, 9, 10, 11 -> {
        if (ok) {
          w.ints(optional ? 1 : 0);
          if (optional) w.opaque(handle());
          w.post(optional, 1, 7);
        }
        w.wcc(optional);
      }
      case 14 -> w.wcc(optional).wcc(optional);
      case 15 -> w.post(optional, 1, 7).wcc(optional);
      default -> throw new AssertionError(procedure);
    }
    return w.build();
  }
}
