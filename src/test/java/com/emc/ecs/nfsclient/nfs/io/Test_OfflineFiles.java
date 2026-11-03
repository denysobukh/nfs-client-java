package com.emc.ecs.nfsclient.nfs.io;

import static org.junit.Assert.*;

import com.emc.ecs.nfsclient.nfs.*;
import com.emc.ecs.nfsclient.nfs.nfs3.Nfs3;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;

/** Exercise file APIs through real RPC transport with RFC 1813 replies; no external export. */
public class Test_OfflineFiles {
  @Test(timeout = 15000)
  public void metadataPathsPermissionsAndDirectoryFilters() throws IOException {
    try (Nfs3Peer peer = new Nfs3Peer()) {
      Nfs3 c = peer.client();
      Nfs3File root = c.newFile("/");
      Nfs3File f = root.newChildFile("abc");
      assertTrue(root.isRootFile());
      assertFalse(f.isRootFile());
      assertEquals("abc", f.getName());
      assertEquals("/abc", f.getPath());
      assertEquals("/", f.getParent());
      assertEquals(root, f.getParentFile());
      assertSame(c, f.getNfs());
      assertEquals("127.0.0.1:/export/abc", f.getAbsolutePath());
      assertEquals(f.getAbsolutePath(), f.toString());
      assertEquals(f, c.newFile("/abc"));
      assertEquals(f.hashCode(), c.newFile("/abc").hashCode());
      assertFalse(f.equals("abc"));
      assertEquals(1, f.compareTo(null));
      assertTrue(f.compareTo(root) > 0);
      assertEquals(root, root.getChildFile("."));
      assertEquals(root, root.getChildFile(".."));
      assertTrue(f.exists());
      assertArrayEquals(Nfs3Wire.handle(), f.getFileHandle());
      assertTrue(f.canRead());
      assertTrue(f.canModify());
      assertTrue(f.canExtend());
      assertTrue(f.canExecute());
      assertTrue(f.canLookup());
      assertTrue(f.canDelete());
      assertEquals(63, f.getAccess(63));
      assertEquals(0644, f.getMode());
      assertTrue(f.isFile());
      assertFalse(f.isDirectory());
      assertEquals(7, f.length());
      assertEquals(7, f.lengthEx());
      assertEquals(20456, f.lastModified());
      assertEquals(800, f.getFreeSpace());
      assertEquals(1000, f.getTotalSpace());
      assertEquals(700, f.getUsableSpace());
      assertEquals(7, f.getattr().getAttributes().getSize());
      assertTrue(f.lookup().stateIsOk());
      assertEquals("target", f.readlink().getData());
      f.setMode(0600);
      f.setLastModified(1234);
      f.setAttributes(new NfsSetAttributes());
      assertEquals(List.of("child"), root.list());
      assertEquals(List.of("child"), root.list((dir, name) -> name.startsWith("c")));
      assertTrue(root.list((dir, name) -> false).isEmpty());
      assertEquals(1, root.list((NfsFilenameFilter) null).size());
      assertEquals("child", root.listFiles().get(0).getName());
      assertEquals(1, root.listFiles((dir, name) -> true).size());
      assertEquals(
          1, root.listFiles((NfsFileFilter) file -> file.getName().equals("child")).size());
      assertTrue(root.listFiles((NfsFileFilter) file -> false).isEmpty());
      assertEquals(1, root.listFiles((NfsFileFilter) null).size());
      assertEquals(4096, root.fsinfo().getFsInfo().rtmax);
      assertEquals(255, root.pathconf().getPosixAttributes().getNameMaximum());
      assertTrue(f.createNewFile());
      assertTrue(
          f.makeCreateRequest(NfsCreateMode.GUARDED, new NfsSetAttributes(), null)
              .toString()
              .contains("abc"));
      f.mkdir();
      f.mkdirs();
      assertTrue(f.symlink("target", new NfsSetAttributes()).stateIsOk());
      assertTrue(f.mknod(NfsType.NFS_FIFO, new NfsSetAttributes(), null).stateIsOk());
      assertTrue(f.link(root.getChildFile("hard")).stateIsOk());
      assertEquals(
          3,
          f.write(0, List.of(ByteBuffer.wrap(new byte[] {1, 2, 3})), 0, Nfs3Wire.VERIFIER)
              .getCount());
      assertEquals(Nfs3Wire.VERIFIER, f.commit(0, 0).getVerf());
      assertTrue(f.renameTo(root.getChildFile("renamed")));
      assertEquals("renamed", f.getName());
      assertEquals("/renamed", f.getPath());
      f.delete();
      assertTrue(f.rmdir().stateIsOk());
    }
  }

  @Test(timeout = 15000)
  public void missingFilesAndGuardedCreateErrors() throws IOException {
    AtomicInteger createStatus = new AtomicInteger(17);
    try (Nfs3Peer peer =
        new Nfs3Peer((p, r) ->
            Nfs3Wire.response(
                r.getInt(0), p, p == 3 ? 2 : p == 8 ? createStatus.get() : 0, false))) {
      Nfs3File f = peer.client().newFile("/missing");
      assertFalse(f.exists());
      assertEquals(0, f.length());
      assertFalse(f.createNewFile());
      createStatus.set(13);
      assertThrows(NfsException.class, f::createNewFile);
    }
  }

  @Test(timeout = 15000)
  public void listCarriesCookiesAndVerifiersAcrossPagesAndOmitsDotEntries() throws IOException {
    AtomicInteger pages = new AtomicInteger();
    try (Nfs3Peer peer =
        new Nfs3Peer((p, r) -> {
          if (p != 16) return Nfs3Wire.response(r.getInt(0), p, 0, true);
          int page = pages.getAndIncrement();
          assertEquals(page == 0 ? 0 : 99, r.getLong(48));
          assertEquals(page == 0 ? 0 : Nfs3Wire.VERIFIER, r.getLong(56));
          Nfs3Wire w = Nfs3Wire.reply(r.getInt(0), 0).ints(0).hyper(Nfs3Wire.VERIFIER);
          if (page == 0)
            w.ints(1).hyper(1).string(".").hyper(98).ints(1).hyper(2).string("..").hyper(99);
          else w.ints(1).hyper(3).string("child").hyper(100);
          return w.ints(0, page == 0 ? 0 : 1).build();
        })) {
      assertEquals(List.of("child"), peer.client().newFile("/").list());
      assertEquals(2, pages.get());
    }
  }

  @Test(timeout = 15000)
  public void streamsHonorShortReadsUnsignedBytesAndCommitWrittenRange() throws IOException {
    byte[] content = {0, (byte) 255, 42, 3, 4, 5, 6};
    List<Byte> written = new ArrayList<>();
    List<Long> offsets = new ArrayList<>();
    AtomicInteger commits = new AtomicInteger();
    try (Nfs3Peer peer =
        new Nfs3Peer((p, r) -> {
          if (p == 6) {
            int offset = (int) r.getLong(48), requested = r.getInt(56);
            int count = Math.min(2, Math.min(requested, content.length - offset));
            return Nfs3Wire.reply(r.getInt(0), 0)
                .ints(0, count, offset + count == content.length ? 1 : 0)
                .opaque(Arrays.copyOfRange(content, offset, offset + count))
                .build();
          }
          if (p == 7) {
            long offset = r.getLong(48);
            int count = r.getInt(56);
            assertEquals(2, r.getInt(60));
            assertEquals(count, r.getInt(64));
            offsets.add(offset);
            for (int i = 0; i < count; i++) written.add(r.get(68 + i));
            return Nfs3Wire.reply(r.getInt(0), 0)
                .wcc(false)
                .ints(count, 2)
                .hyper(Nfs3Wire.VERIFIER)
                .build();
          }
          if (p == 21) {
            assertEquals(10, r.getLong(48));
            assertEquals(5, r.getInt(56));
            commits.incrementAndGet();
          }
          return Nfs3Wire.response(r.getInt(0), p, 0, true);
        })) {
      Nfs3File f = peer.client().newFile("/abc");
      try (NfsFileInputStream in = new NfsFileInputStream(f, 3)) {
        assertFalse(in.markSupported());
        in.mark(1);
        assertThrows(IOException.class, in::reset);
        assertEquals(7, in.available());
        assertEquals(0, in.read());
        assertEquals(255, in.read());
        assertEquals(2, in.skip(2));
        byte[] tail = new byte[3];
        assertEquals(3, in.read(tail));
        assertArrayEquals(new byte[] {4, 5, 6}, tail);
        assertEquals(-1, in.read());
        assertEquals(0, in.read(new byte[0]));
        assertEquals(0, in.skip(100));
        assertThrows(NullPointerException.class, () -> assertEquals(-1, in.read(null, 0, 1)));
        assertThrows(
            IndexOutOfBoundsException.class, () -> assertEquals(-1, in.read(new byte[1], 1, 1)));
        in.close();
        assertThrows(IOException.class, in::available);
        assertThrows(IOException.class, () -> assertEquals(0, in.skip(1)));
      }
      try (NfsFileInputStream in = new NfsFileInputStream(f)) {
        assertEquals(7, in.read(new byte[20]));
      }
      try (NfsFileInputStream in = new NfsFileInputStream(f, 2, 3)) {
        assertEquals(42, in.read());
      }
      assertThrows(IllegalArgumentException.class, () -> new NfsFileInputStream(f, -1, 3).close());
      assertThrows(IllegalArgumentException.class, () -> new NfsFileInputStream(f, 0).close());
      try (NfsFileOutputStream out = new NfsFileOutputStream(f, 10, 2)) {
        out.write(new byte[] {1, 2, 3, 4});
        out.write(5);
        out.write(new byte[0]);
        out.flush();
        out.flush();
        assertThrows(NullPointerException.class, () -> out.write(null, 0, 1));
        assertThrows(IndexOutOfBoundsException.class, () -> out.write(new byte[1], 1, 1));
        out.close();
        assertThrows(IOException.class, () -> out.write(6));
        assertThrows(IOException.class, out::flush);
      }
      assertEquals(List.of((byte) 1, (byte) 2, (byte) 3, (byte) 4, (byte) 5), written);
      assertEquals(List.of(10L), offsets);
      assertEquals(1, commits.get());
      assertThrows(IllegalArgumentException.class, () -> new NfsFileOutputStream(f, -1, 2).close());
      assertThrows(IllegalArgumentException.class, () -> new NfsFileOutputStream(f, 3).close());
    }
  }

  @Test(timeout = 15000)
  public void flushRetriesShortWritesBeforeCommitting() throws IOException {
    List<Byte> written = new ArrayList<>();
    AtomicInteger commits = new AtomicInteger();
    try (Nfs3Peer peer =
        new Nfs3Peer((p, r) -> {
          if (p == 7) {
            assertEquals(10 + written.size(), r.getLong(48));
            int requested = r.getInt(56);
            int count = Math.min(2, requested);
            for (int j = 0; j < count; j++) written.add(r.get(68 + j));
            return Nfs3Wire.reply(r.getInt(0), 0)
                .wcc(false)
                .ints(count, 2)
                .hyper(Nfs3Wire.VERIFIER)
                .build();
          }
          if (p == 21) {
            assertEquals(5, written.size());
            assertEquals(10, r.getLong(48));
            assertEquals(5, r.getInt(56));
            commits.incrementAndGet();
          }
          return Nfs3Wire.response(r.getInt(0), p, 0, true);
        })) {
      try (NfsFileOutputStream out =
          new NfsFileOutputStream(peer.client().newFile("/abc"), 10, 2)) {
        out.write(new byte[] {1, 2, 3, 4, 5});
        out.flush();
      }
      assertEquals(List.of((byte) 1, (byte) 2, (byte) 3, (byte) 4, (byte) 5), written);
      assertEquals(1, commits.get());
    }
  }

  @Test(timeout = 15000)
  public void zeroProgressWriteFailsWithoutCommitting() throws IOException {
    AtomicInteger commits = new AtomicInteger();
    try (Nfs3Peer peer =
        new Nfs3Peer((p, r) -> {
          if (p == 7)
            return Nfs3Wire.reply(r.getInt(0), 0)
                .wcc(false)
                .ints(0, 2)
                .hyper(Nfs3Wire.VERIFIER)
                .build();
          if (p == 21) commits.incrementAndGet();
          return Nfs3Wire.response(r.getInt(0), p, 0, true);
        })) {
      try (NfsFileOutputStream out = new NfsFileOutputStream(peer.client().newFile("/abc"))) {
        out.write(1);
        assertThrows(IOException.class, out::flush);
      }
      assertEquals(0, commits.get());
    }
  }
}
