package com.emc.ecs.nfsclient.nfs.nfs3;

import static org.junit.Assert.*;

import com.emc.ecs.nfsclient.nfs.*;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.*;
import org.apache.commons.lang3.mutable.MutableBoolean;
import org.junit.Test;

public class Test_Nfs3Calls {
  @Test(timeout = 15000)
  public void nakedAndWrappedCallsDecodeRealTcpReplies() throws IOException {
    try (Nfs3Peer peer = new Nfs3Peer()) {
      Nfs3 c = peer.client();
      byte[] h = Nfs3Wire.handle();
      NfsSetAttributes a = new NfsSetAttributes();
      assertTrue(c.getAttr(c.makeGetAttrRequest(h)).stateIsOk());
      assertEquals(7, c.wrapped_getAttr(c.makeGetAttrRequest(h)).getAttributes().getSize());
      assertTrue(c.setAttr(c.makeSetAttrRequest(h, a, null)).stateIsOk());
      assertTrue(c.wrapped_setAttr(c.makeSetAttrRequest(h, a, null)).stateIsOk());
      assertTrue(c.getLookup(c.makeLookupRequest(h, "abc")).stateIsOk());
      assertArrayEquals(h, c.wrapped_getLookup(c.makeLookupRequest(h, "abc")).getFileHandle());
      assertEquals(63, c.getAccess(c.makeAccessRequest(h, 63)).getAccess());
      assertEquals(63, c.wrapped_getAccess(c.makeAccessRequest(h, 63)).getAccess());
      assertEquals("target", c.getReadlink(c.makeReadlinkRequest(h)).getData());
      assertEquals("target", c.wrapped_getReadlink(c.makeReadlinkRequest(h)).getData());
      assertEquals(3, c.getRead(c.makeReadRequest(h, 0, 3), null, 0).getBytesRead());
      byte[] data = new byte[5];
      assertEquals(3, c.wrapped_getRead(c.makeReadRequest(h, 0, 3), data, 1).getBytesRead());
      assertArrayEquals(new byte[] {0, 0, (byte) 255, 42, 0}, data);
      List<ByteBuffer> payload = List.of(ByteBuffer.wrap(new byte[] {0, (byte) 255, 42}));
      assertEquals(3, c.sendWrite(c.makeWriteRequest(h, 0, payload, 2)).getCount());
      assertEquals(3, c.wrapped_sendWrite(c.makeWriteRequest(h, 0, payload, 2)).getCount());
      assertEquals(
          3,
          c.wrapped_sendWrite(c.makeWriteRequest(h, 0, payload, 0), Nfs3Wire.VERIFIER).getCount());
      assertTrue(
          c.sendCreate(c.makeCreateRequest(NfsCreateMode.GUARDED, h, "abc", a, null)).stateIsOk());
      assertTrue(
          c.wrapped_sendCreate(c.makeCreateRequest(NfsCreateMode.GUARDED, h, "abc", a, null))
              .stateIsOk());
      assertTrue(c.sendMkdir(c.makeMkdirRequest(h, "abc", a)).stateIsOk());
      assertTrue(c.wrapped_sendMkdir(c.makeMkdirRequest(h, "abc", a)).stateIsOk());
      assertTrue(c.sendSymlink(c.makeSymlinkRequest("target", h, "abc", a)).stateIsOk());
      assertTrue(c.wrapped_sendSymlink(c.makeSymlinkRequest("target", h, "abc", a)).stateIsOk());
      assertTrue(c.sendMknod(c.makeMknodRequest(h, "abc", NfsType.NFS_FIFO, a, null)).stateIsOk());
      assertTrue(
          c.wrapped_sendMknod(c.makeMknodRequest(h, "abc", NfsType.NFS_FIFO, a, null)).stateIsOk());
      assertTrue(c.sendRemove(c.makeRemoveRequest(h, "abc")).stateIsOk());
      assertTrue(c.wrapped_sendRemove(c.makeRemoveRequest(h, "abc")).stateIsOk());
      assertTrue(c.sendRmdir(c.makeRmdirRequest(h, "abc")).stateIsOk());
      assertTrue(c.wrapped_sendRmdir(c.makeRmdirRequest(h, "abc")).stateIsOk());
      assertTrue(c.sendRename(c.makeRenameRequest(h, "abc", h, "def")).stateIsOk());
      assertTrue(c.wrapped_sendRename(c.makeRenameRequest(h, "abc", h, "def")).stateIsOk());
      assertTrue(c.sendLink(c.makeLinkRequest(h, h, "abc")).stateIsOk());
      assertTrue(c.wrapped_sendLink(c.makeLinkRequest(h, h, "abc")).stateIsOk());
      assertEquals(1, c.getReaddir(c.makeReaddirRequest(h, 0, 0, 1024)).getEntries().size());
      assertTrue(c.wrapped_getReaddir(c.makeReaddirRequest(h, 0, 0, 1024)).isEof());
      List<NfsDirectoryEntry> entries = new ArrayList<>();
      c.wrapped_getReaddir(c.makeReaddirRequest(h, 0, 0, 1024), entries);
      assertEquals(1, entries.size());
      assertEquals(
          1, c.getReaddirplus(c.makeReaddirplusRequest(h, 0, 0, 1024, 4096)).getEntries().size());
      assertTrue(c.wrapped_getReaddirplus(c.makeReaddirplusRequest(h, 0, 0, 1024, 4096)).isEof());
      List<NfsDirectoryPlusEntry> plus = new ArrayList<>();
      c.wrapped_getReaddirplus(c.makeReaddirplusRequest(h, 0, 0, 1024, 4096), plus);
      assertEquals(1, plus.size());
      assertEquals(1000, c.getFsStat(c.makeFsStatRequest()).getFsStat().tbytes);
      assertEquals(1000, c.getNfsFsStat().tbytes);
      assertEquals(4096, c.getFsInfo(c.makeFsInfoRequest()).getFsInfo().rtmax);
      assertEquals(4096, c.getNfsFsInfo().rtmax);
      assertEquals(
          255, c.getPathconf(c.makePathconfRequest(h)).getPosixAttributes().getNameMaximum());
      assertTrue(c.wrapped_getPathconf(c.makePathconfRequest(h)).stateIsOk());
      assertEquals(Nfs3Wire.VERIFIER, c.sendCommit(c.makeCommitRequest(h, 0, 0)).getVerf());
      assertEquals(Nfs3Wire.VERIFIER, c.wrapped_sendCommit(c.makeCommitRequest(h, 0, 0)).getVerf());
      MutableBoolean eof = new MutableBoolean();
      assertEquals(3, c.read("/abc", h, 0, 3, data, 0, eof));
      assertTrue(eof.booleanValue());
      c.fsync("/abc", h, Nfs3Wire.VERIFIER);
      c.fsync("/abc", h, null);
      assertThrows(NfsException.class, () -> c.fsync("/abc", h, 0L));
      assertThrows(
          NfsException.class, () -> c.wrapped_sendWrite(c.makeWriteRequest(h, 0, payload, 0), 0L));
    }
  }

  @Test(timeout = 15000)
  public void wrappedErrorsPreserveNfsStatusWhileNakedCallsReturnIt() throws IOException {
    try (Nfs3Peer peer = new Nfs3Peer((p, r) -> Nfs3Wire.response(r.getInt(0), p, 13, false))) {
      Nfs3 c = peer.client();
      assertEquals(13, c.getAttr(c.makeGetAttrRequest(Nfs3Wire.handle())).getState());
      NfsException error =
          assertThrows(
              NfsException.class, () -> c.wrapped_getAttr(c.makeGetAttrRequest(Nfs3Wire.handle())));
      assertEquals(NfsStatus.NFS3ERR_ACCES, error.getStatus());
    }
  }
}
