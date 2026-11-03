package com.emc.ecs.nfsclient.nfs.nfs3;

import static org.junit.Assert.*;

import com.emc.ecs.nfsclient.nfs.*;
import com.emc.ecs.nfsclient.rpc.*;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.*;
import org.junit.Test;

/** Wire assertions are derived from RFC 1813 sections 2.5 and 3.3, not encoder round trips. */
public class Test_Nfs3Protocol {
  private final Nfs3 client;

  public Test_Nfs3Protocol() throws IOException {
    client = new Nfs3("127.0.0.1", "/export", Nfs3Wire.handle(), new CredentialNone(), 1, 65535, 0);
  }

  private void request(int procedure, NfsRequestBase request, Nfs3Wire body) {
    Xdr x = new Xdr(4096);
    request.marshalling(x);
    byte[] expected = body.build();
    ByteBuffer header = ByteBuffer.wrap(x.getBuffer());
    assertEquals(0, header.getInt(4)); // CALL
    assertEquals(2, header.getInt(8)); // RPC version
    assertEquals(100003, header.getInt(12));
    assertEquals(3, header.getInt(16));
    assertEquals(procedure, header.getInt(20));
    assertEquals(0, header.getInt(24)); // AUTH_NONE
    assertEquals(0, header.getInt(28));
    assertEquals(0, header.getInt(32));
    assertEquals(0, header.getInt(36));
    assertArrayEquals(expected, Arrays.copyOfRange(x.getBuffer(), 40, x.getOffset()));
    assertTrue(request.getErrorMessage().contains(request.toString()));
    assertArrayEquals(Nfs3Wire.handle(), request.getIpKey());
  }

  private Nfs3Wire handle() {
    return new Nfs3Wire().opaque(Nfs3Wire.handle());
  }

  private Nfs3Wire emptyAttributes(Nfs3Wire w) {
    return w.ints(0, 0, 0, 0, 0, 0);
  }

  @Test
  public void allProcedureArgumentsFollowRfc1813() throws IOException {
    byte[] h = Nfs3Wire.handle();
    NfsSetAttributes a = new NfsSetAttributes();
    request(1, client.makeGetAttrRequest(h), handle());
    request(2, client.makeSetAttrRequest(h, a, null), emptyAttributes(handle()).ints(0));
    request(
        2,
        client.makeSetAttrRequest(h, a, new NfsTime(1234, false)),
        emptyAttributes(handle()).ints(1, 1, 234000000));
    request(3, client.makeLookupRequest(h, "abc"), handle().string("abc"));
    request(4, client.makeAccessRequest(h, 63), handle().ints(63));
    request(5, client.makeReadlinkRequest(h), handle());
    request(6, client.makeReadRequest(h, 1L << 40, 4096), handle().hyper(1L << 40).ints(4096));
    for (NfsCreateMode mode : new NfsCreateMode[] {NfsCreateMode.UNCHECKED, NfsCreateMode.GUARDED})
      request(
          8,
          client.makeCreateRequest(mode, h, "abc", a, null),
          emptyAttributes(
              handle().string("abc").ints(mode.equals(NfsCreateMode.UNCHECKED) ? 0 : 1)));
    byte[] verf = {1, 2, 3, 4, 5, 6, 7, 8};
    request(
        8,
        client.makeCreateRequest(NfsCreateMode.EXCLUSIVE, h, "abc", null, verf),
        handle().string("abc").ints(2).hyper(0x0102030405060708L));
    request(9, client.makeMkdirRequest(h, "abc", a), emptyAttributes(handle().string("abc")));
    request(
        10,
        client.makeSymlinkRequest("../target", h, "abc", a),
        emptyAttributes(handle().string("abc")).string("../target"));
    for (int type = 3; type <= 7; type++) {
      if (type == 5) continue; // symlinks use SYMLINK, not MKNOD
      Nfs3Wire expected = emptyAttributes(handle().string("abc").ints(type));
      if (type <= 4) expected.ints(8, 9);
      request(
          11,
          client.makeMknodRequest(h, "abc", NfsType.fromValue(type), a, new long[] {8, 9}),
          expected);
    }
    request(12, client.makeRemoveRequest(h, "abc"), handle().string("abc"));
    request(13, client.makeRmdirRequest(h, "abc"), handle().string("abc"));
    request(
        14,
        client.makeRenameRequest(h, "abc", h, "def"),
        handle().string("abc").opaque(h).string("def"));
    request(15, client.makeLinkRequest(h, h, "abc"), handle().opaque(h).string("abc"));
    request(
        16,
        client.makeReaddirRequest(h, 99, Nfs3Wire.VERIFIER, 1024),
        handle().hyper(99).hyper(Nfs3Wire.VERIFIER).ints(1024));
    request(
        17,
        client.makeReaddirplusRequest(h, 99, Nfs3Wire.VERIFIER, 1024, 4096),
        handle().hyper(99).hyper(Nfs3Wire.VERIFIER).ints(1024, 4096));
    request(18, client.makeFsStatRequest(), handle());
    request(19, client.makeFsInfoRequest(), handle());
    request(20, client.makePathconfRequest(h), handle());
    request(21, client.makeCommitRequest(h, 1L << 40, 0), handle().hyper(1L << 40).ints(0));
  }

  @Test
  public void writePayloadCountStabilityAndPaddingFollowRfc1813() throws IOException {
    for (int stable = 0; stable <= 2; stable++)
      for (int size = 0; size <= 5; size++) {
        byte[] data = new byte[size];
        Arrays.fill(data, (byte) 0xab);
        NfsWriteRequest r =
            client.makeWriteRequest(
                Nfs3Wire.handle(), 1L << 40, List.of(ByteBuffer.wrap(data)), stable);
        assertEquals(size, r.getSize());
        assertEquals(stable == 2, r.isSync());
        Xdr x = new Xdr(1024);
        r.marshalling(x);
        // Capture the header before requesting the scatter/gather payload buffers, as transport
        // does.
        byte[] header = Arrays.copyOfRange(x.getBuffer(), 40, x.getOffset());
        List<ByteBuffer> payload = x.getPayloads();
        ByteBuffer actual = ByteBuffer.allocate(header.length + size + 4);
        actual.put(header);
        for (ByteBuffer part : payload) actual.put(part.duplicate());
        assertArrayEquals(
            handle().hyper(1L << 40).ints(size, stable).opaque(data).build(),
            Arrays.copyOf(actual.array(), actual.position()));
        assertTrue(r.toString().contains("syncType:" + stable));
      }
    List<ByteBuffer> emptyPayload = List.of();
    byte[] h = Nfs3Wire.handle();
    assertThrows(
        IllegalArgumentException.class, () -> client.makeWriteRequest(h, 0, emptyPayload, 3));
  }

  @Test
  public void exclusiveVerifierIsFixedLengthAndStableAcrossRetries() throws IOException {
    NfsCreateRequest r =
        client.makeCreateRequest(NfsCreateMode.EXCLUSIVE, Nfs3Wire.handle(), "abc", null, null);
    assertEquals(8, r.getVerifier().length);
    Nfs3Wire expected =
        handle().string("abc").ints(2).hyper(ByteBuffer.wrap(r.getVerifier()).getLong());
    request(8, r, expected);
    request(8, r, expected);
    for (int length : new int[] {0, 7, 9})
      assertThrows(
          IllegalArgumentException.class,
          () ->
              client.makeCreateRequest(
                  NfsCreateMode.EXCLUSIVE, Nfs3Wire.handle(), "abc", null, new byte[length]));
  }

  @Test
  public void setattrUsesOptionalValuesAndTimeHowDiscriminants() {
    NfsSetAttributes a = new NfsSetAttributes();
    a.setMode(0644L);
    a.setUid(0xffffffffL);
    a.setGid(42L);
    a.setSize(1L << 40);
    a.setAtime(NfsTime.SET_TO_CURRENT_ON_SERVER);
    a.setMtime(new NfsTime(1234));
    Xdr x = new Xdr(128);
    a.marshalling(x);
    assertArrayEquals(
        new Nfs3Wire()
            .ints(1, 0644, 1, -1, 1, 42, 1)
            .hyper(1L << 40)
            .ints(1, 2, 1, 234000000)
            .build(),
        Arrays.copyOf(x.getBuffer(), x.getOffset()));
    assertTrue(a.toString().contains("644") || a.toString().contains("420"));
    NfsTime bareTime = new NfsTime(1, false);
    assertThrows(IllegalArgumentException.class, () -> a.setAtime(bareTime));
    assertThrows(IllegalArgumentException.class, () -> a.setMtime(bareTime));
    NfsTime settingTime = new NfsTime(1);
    byte[] h = Nfs3Wire.handle();
    assertThrows(
        IllegalArgumentException.class, () -> client.makeSetAttrRequest(h, a, settingTime));
  }

  private NfsResponseBase response(int p) {
    return switch (p) {
      case 1 -> new Nfs3GetAttrResponse();
      case 2 -> new Nfs3SetAttrResponse();
      case 3 -> new Nfs3LookupResponse();
      case 4 -> new Nfs3AccessResponse();
      case 5 -> new Nfs3ReadlinkResponse();
      case 6 -> new Nfs3ReadResponse(null, 0);
      case 7 -> new Nfs3WriteResponse();
      case 8 -> new Nfs3CreateResponse();
      case 9 -> new Nfs3MkdirResponse();
      case 10 -> new Nfs3SymlinkResponse();
      case 11 -> new Nfs3MknodResponse();
      case 12 -> new Nfs3RemoveResponse();
      case 13 -> new Nfs3RmdirResponse();
      case 14 -> new Nfs3RenameResponse();
      case 15 -> new Nfs3LinkResponse();
      case 16 -> new Nfs3ReaddirResponse();
      case 17 -> new Nfs3ReaddirplusResponse();
      case 18 -> new Nfs3FsStatResponse();
      case 19 -> new Nfs3FsInfoResponse();
      case 20 -> new Nfs3PathconfResponse();
      case 21 -> new Nfs3CommitResponse();
      default -> throw new AssertionError(p);
    };
  }

  @Test
  public void successAndFailureUnionArmsConsumeExactlyTheirRfcFields() throws IOException {
    for (int p = 1; p <= 21; p++)
      for (boolean optional : new boolean[] {false, true})
        for (int status : new int[] {0, 13}) {
          byte[] wire = Nfs3Wire.response(123, p, status, optional);
          Xdr x = new Xdr(wire);
          NfsResponseBase r = response(p);
          r.unmarshalling(x);
          assertEquals("procedure " + p, wire.length, x.getOffset());
          assertEquals(status, r.getState());
          assertEquals(status == 0, r.stateIsOk());
          assertEquals(123, r.getXid());
          assertEquals(1, r.getDirection());
          assertEquals(0, r.getReplyStatus());
          assertEquals(0, r.getAcceptStatus());
          assertTrue(r.toString().contains("123"));
          if (status == 0) checkSuccess(p, r, optional);
        }
  }

  private void checkSuccess(int p, NfsResponseBase r, boolean optional) {
    if (p == 1) checkAttributes(r.getAttributes());
    if (p == 3) {
      assertArrayEquals(Nfs3Wire.handle(), r.getFileHandle());
      assertEquals(optional, ((NfsLookupResponse) r).getDirectoryAttributes() != null);
    }
    if (p == 4) assertEquals(63, ((NfsAccessResponse) r).getAccess());
    if (p == 5) assertEquals("target", ((NfsReadlinkResponse) r).getData());
    if (p == 6) {
      NfsReadResponse read = (NfsReadResponse) r;
      assertEquals(3, read.getBytesRead());
      assertTrue(read.isEof());
      assertArrayEquals(new byte[] {0, (byte) 255, 42}, read.getBytes());
    }
    if (p == 7) {
      NfsWriteResponse w = (NfsWriteResponse) r;
      assertEquals(3, w.getCount());
      assertEquals(2, w.getCommitted());
      assertEquals(Nfs3Wire.VERIFIER, w.getVerf());
      checkWcc(w.getFileWccData(), optional);
    }
    if (p >= 8 && p <= 11) assertEquals(optional, r.getFileHandle() != null);
    if (p == 8) checkWcc(((NfsCreateResponse) r).getDirectoryWccData(), optional);
    if (p == 9) checkWcc(((NfsMkdirResponse) r).getDirectoryWccData(), optional);
    if (p == 10) checkWcc(((NfsSymlinkResponse) r).getDirectoryWccData(), optional);
    if (p == 11) checkWcc(((NfsMknodResponse) r).getDirectoryWccData(), optional);
    if (p == 12) checkWcc(((NfsRemoveResponse) r).getDirectoryWccData(), optional);
    if (p == 13) checkWcc(((NfsRmdirResponse) r).getDirectoryWccData(), optional);
    if (p == 14) {
      checkWcc(((NfsRenameResponse) r).getFromDirectoryWccData(), optional);
      checkWcc(((NfsRenameResponse) r).getToDirectoryWccData(), optional);
    }
    if (p == 15) checkWcc(((NfsLinkResponse) r).getDirectoryWccData(), optional);
    if (p == 16) {
      NfsReaddirResponse d = (NfsReaddirResponse) r;
      assertEquals(99, d.getCookie());
      assertEquals(Nfs3Wire.VERIFIER, d.getCookieverf());
      assertTrue(d.isEof());
      assertEquals("child", d.getEntries().get(0).getFileName());
      assertEquals(12, d.getEntries().get(0).getFileId());
    }
    if (p == 17) {
      NfsReaddirplusResponse d = (NfsReaddirplusResponse) r;
      assertEquals(99, d.getCookie());
      assertEquals(Nfs3Wire.VERIFIER, d.getCookieverf());
      assertTrue(d.isEof());
      NfsDirectoryPlusEntry e = d.getEntries().get(0);
      assertEquals("child", e.getFileName());
      assertEquals(optional, e.getAttributes() != null);
      assertEquals(optional, e.getFileHandle() != null);
    }
    if (p == 18) {
      NfsFsStat s = ((NfsFsStatResponse) r).getFsStat();
      assertEquals(1000, s.tbytes);
      assertEquals(800, s.fbytes);
      assertEquals(700, s.bytes);
      assertEquals(600, s.tfiles);
      assertEquals(500, s.ffiles);
      assertEquals(400, s.afiles);
      assertEquals(0xffffffffL, s.invarsec);
      assertTrue(s.toString().contains("1000"));
    }
    if (p == 19) {
      NfsFsInfo f = ((NfsFsInfoResponse) r).getFsInfo();
      assertEquals(4096, f.rtmax);
      assertEquals(2048, f.rtpref);
      assertEquals(4, f.rtmult);
      assertEquals(4096, f.wtmax);
      assertEquals(2048, f.wtpref);
      assertEquals(4, f.wtmult);
      assertEquals(1024, f.dtpref);
      assertEquals(1L << 40, f.maxfilesize);
      assertEquals(27, f.properties);
      assertTrue(f.toString().contains("4096"));
    }
    if (p == 20) {
      NfsPosixAttributes a = ((NfsPathconfResponse) r).getPosixAttributes();
      assertTrue(a.isLoaded());
      assertEquals(32000, a.getLinkMaximum());
      assertEquals(255, a.getNameMaximum());
      assertTrue(a.isNoTruncation());
      assertTrue(a.isChownRestricted());
      assertFalse(a.isCaseInsensitive());
      assertTrue(a.isCasePreserving());
      assertTrue(a.toString().contains("255"));
    }
    if (p == 21) {
      assertEquals(Nfs3Wire.VERIFIER, ((NfsCommitResponse) r).getVerf());
      checkWcc(((NfsCommitResponse) r).getFileWccData(), optional);
    }
  }

  private void checkWcc(NfsWccData w, boolean present) {
    assertEquals(present, w.getPreOpAttributes() != null);
    assertEquals(present, w.getAttributes() != null);
    if (present) {
      assertEquals(7, w.getPreOpAttributes().getSize());
      assertEquals(20456, w.getPreOpAttributes().getMTime().getTimeInMillis());
      assertEquals(30789, w.getPreOpAttributes().getCTime().getTimeInMillis());
    }
  }

  private void checkAttributes(NfsGetAttributes a) {
    assertTrue(a.isLoaded());
    assertEquals(NfsType.NFS_REG, a.getType());
    assertEquals(0644, a.getMode());
    assertEquals(2, a.getNlink());
    assertEquals(0xffffffffL, a.getUid());
    assertEquals(0xfffffffeL, a.getGid());
    assertEquals(7, a.getSize());
    assertEquals(4096, a.getUsed());
    assertArrayEquals(new long[] {8, 9}, a.getRdev());
    assertEquals(11, a.getFsid());
    assertEquals(12, a.getFileid());
    assertEquals(10123, a.getAtime().getTimeInMillis());
    assertEquals(20456, a.getMtime().getTimeInMillis());
    assertEquals(30789, a.getCtime().getTimeInMillis());
    assertTrue(a.toString().contains("_size=7"));
  }
}
