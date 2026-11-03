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
package com.emc.ecs.nfsclient.nfs.nfs3;

import com.emc.ecs.nfsclient.nfs.NfsGetAttrRequest;
import com.emc.ecs.nfsclient.rpc.Credential;
import java.io.FileNotFoundException;

/**
 * The request, as specified by RFC 1813 (https://tools.ietf.org/html/rfc1813).
 *
 * <p>Procedure GETATTR retrieves the attributes for a specified file system object. The object is
 * identified by the file handle that the server returned as part of the response from a LOOKUP,
 * CREATE, MKDIR, SYMLINK, MKNOD, or READDIRPLUS procedure (or from the MOUNT service, described
 * elsewhere).
 *
 * @author seibed
 */
public class Nfs3GetAttrRequest extends NfsGetAttrRequest {

  /**
   * Creates the request, as specified by RFC 1813 (https://tools.ietf.org/html/rfc1813).
   *
   * <p>Procedure GETATTR retrieves the attributes for a specified file system object. The object is
   * identified by the file handle that the server returned as part of the response from a LOOKUP,
   * CREATE, MKDIR, SYMLINK, MKNOD, or READDIRPLUS procedure (or from the MOUNT service, described
   * elsewhere).
   *
   * @param fileHandle The file handle of an object whose attributes are to be retrieved.
   * @param credential The credential to use for the request
   */
  public Nfs3GetAttrRequest(byte[] fileHandle, Credential credential) throws FileNotFoundException {
    super(fileHandle, credential, Nfs3.VERSION);
  }
}
