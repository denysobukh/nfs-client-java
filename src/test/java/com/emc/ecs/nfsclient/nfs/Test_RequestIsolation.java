package com.emc.ecs.nfsclient.nfs;

import static org.junit.Assert.*;

import com.emc.ecs.nfsclient.nfs.nfs3.Nfs3LookupRequest;
import com.emc.ecs.nfsclient.rpc.CredentialNone;
import com.emc.ecs.nfsclient.rpc.Xdr;
import java.io.FileNotFoundException;
import org.junit.Test;

public class Test_RequestIsolation {
  @Test
  public void requestHandleCannotBeChangedThroughInputOrGetters() throws FileNotFoundException {
    byte[] handle = {1, 2, 3, 4};
    Nfs3LookupRequest request = new Nfs3LookupRequest(handle, "child", new CredentialNone());
    handle[0] = 9;
    request.getFileHandle()[1] = 9;
    request.getIpKey()[2] = 9;
    assertArrayEquals(new byte[] {1, 2, 3, 4}, request.getFileHandle());
  }

  @Test
  public void exclusiveCreateVerifierIsStableAcrossRetries() throws FileNotFoundException {
    byte[] verifier = {1, 2, 3, 4, 5, 6, 7, 8};
    NfsCreateRequest request =
        new NfsCreateRequest(
            NfsCreateMode.EXCLUSIVE,
            new byte[] {1},
            "child",
            null,
            verifier,
            new CredentialNone(),
            3);
    verifier[0] = 9;
    request.getVerifier()[1] = 9;
    assertArrayEquals(new byte[] {1, 2, 3, 4, 5, 6, 7, 8}, request.getVerifier());
    NfsCreateRequest ordinary =
        new NfsCreateRequest(
            NfsCreateMode.UNCHECKED,
            new byte[] {1},
            "child",
            new NfsSetAttributes(),
            null,
            new CredentialNone(),
            3);
    assertNull(ordinary.getVerifier());
  }

  @Test
  public void directoryHandleAndDeviceNumbersAreSnapshots() {
    Xdr wire = new Xdr(64);
    wire.putLong(1);
    wire.putString("child");
    wire.putLong(2);
    wire.putBoolean(false); // No attributes.
    wire.putBoolean(true); // A file handle follows.
    wire.putByteArray(new byte[] {1, 2, 3, 4});
    wire.setOffset(0);
    NfsDirectoryPlusEntry entry = new NfsDirectoryPlusEntry(wire);
    entry.getFileHandle()[0] = 9;
    assertArrayEquals(new byte[] {1, 2, 3, 4}, entry.getFileHandle());

    NfsGetAttributes attributes = new NfsGetAttributes();
    attributes.getRdev()[0] = 9;
    assertArrayEquals(new long[] {0, 0}, attributes.getRdev());
  }
}
