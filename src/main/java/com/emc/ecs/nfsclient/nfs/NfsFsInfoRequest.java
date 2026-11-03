/**
 * Copyright 2016-2018 Dell Inc. or its subsidiaries. All rights reserved.
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"). You may not use this file
 * except in compliance with the License. A copy of the License is located at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0.txt
 *
 * <p>or in the "license" file accompanying this file. This file is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package com.emc.ecs.nfsclient.nfs;

import com.emc.ecs.nfsclient.rpc.Credential;
import java.io.FileNotFoundException;

/**
 * The request, as specified by RFC 1813 (https://tools.ietf.org/html/rfc1813).
 *
 * <p>Procedure FSINFO retrieves nonvolatile file system state information and general information
 * about the NFS version 3 protocol server implementation.
 *
 * @author seibed
 */
public class NfsFsInfoRequest extends NfsRequestBase {

  /**
   * Creates the request, as specified by RFC 1813 (https://tools.ietf.org/html/rfc1813).
   *
   * <p>Procedure FSINFO retrieves nonvolatile file system state information and general information
   * about the NFS version 3 protocol server implementation.
   *
   * @param fileHandle A file handle identifying a file object. Normal usage is to provide a file
   *     handle for a mount point for a file system, as originally obtained from the MOUNT service
   *     on the server.
   * @param credential The credential used for RPC authentication.
   * @param nfsVersion The NFS version number
   */
  public NfsFsInfoRequest(byte[] fileHandle, Credential credential, int nfsVersion)
      throws FileNotFoundException {
    super(Nfs.RPC_PROGRAM, nfsVersion, Nfs.NFSPROC3_FSINFO, credential, fileHandle);
  }

  /** {@inheritDoc} */
  @Override
  public String toString() {
    return startToString("NfsFsInfoRequest").toString();
  }
}
