package com.emc.ecs.nfsclient.rpc;

import static org.junit.Assert.*;

import com.emc.ecs.nfsclient.mount.MountException;
import com.emc.ecs.nfsclient.mount.MountStatus;
import com.emc.ecs.nfsclient.nfs.NfsException;
import com.emc.ecs.nfsclient.nfs.NfsStatus;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import org.junit.Test;

public class Test_ExceptionSerialization {
  @Test
  public void exceptionRoundTripsPreserveStatusAndCause()
      throws IOException, ClassNotFoundException {
    for (RpcStatus status :
        new RpcStatus[] {RpcStatus.NETWORK_ERROR, RpcStatus.LOCAL_BINDING_ERROR}) {
      RpcException copy =
          (RpcException) roundTrip(new RpcException(status, "rpc", new IOException("cause")));
      assertSame(status, copy.getStatus());
      assertEquals(status.toString(), copy.getStatus().toString());
      assertEquals("rpc", copy.getMessage());
      assertEquals("cause", copy.getCause().getMessage());
    }
    NfsException nfs = (NfsException) roundTrip(new NfsException(NfsStatus.NFS3ERR_IO, "nfs"));
    assertSame(NfsStatus.NFS3ERR_IO, nfs.getStatus());
    MountException mount =
        (MountException) roundTrip(new MountException(MountStatus.MNT3ERR_ACCES, "mount"));
    assertSame(MountStatus.MNT3ERR_ACCES, mount.getStatus());
  }

  @Test
  public void unknownStatusesKeepTheirWireCodes() throws IOException, ClassNotFoundException {
    NfsException nfs =
        (NfsException) roundTrip(new NfsException(NfsStatus.fromValue(54321), "nfs"));
    assertEquals(54321, nfs.getStatus().getValue());
    MountException mount =
        (MountException) roundTrip(new MountException(MountStatus.fromValue(54321), "mount"));
    assertEquals(54321, mount.getStatus().getValue());
  }

  private static Object roundTrip(IOException error) throws IOException, ClassNotFoundException {
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
      output.writeObject(error);
    }
    try (ObjectInputStream input =
        new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
      return input.readObject();
    }
  }
}
