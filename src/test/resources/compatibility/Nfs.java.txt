/**
 * Copyright 2016-2018 Dell Inc. or its subsidiaries. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 * You may not use this file except in compliance with the License.
 * A copy of the License is located at
 *
 * http://www.apache.org/licenses/LICENSE-2.0.txt
 *
 * or in the "license" file accompanying this file. This file is distributed
 * on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */
// Frozen pre-issue-36 interface for source and binary compatibility tests.
package com.emc.ecs.nfsclient.nfs;
import com.emc.ecs.nfsclient.nfs.io.NfsFile;
import com.emc.ecs.nfsclient.rpc.Credential;
import com.emc.ecs.nfsclient.rpc.Xdr;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;
public interface Nfs<F extends NfsFile<?, ?>> {
    static final int RPC_PROGRAM = 100003;
    public final static int MOUNTPROG = 100005;
    final static int NFSPROC3_NULL = 0;
    final static int NFSPROC3_GETATTR = 1;
    final static int NFSPROC3_SETATTR = 2;
    final static int NFSPROC3_LOOKUP = 3;
    final static int NFSPROC3_ACCESS = 4;
    final static int NFSPROC3_READLINK = 5;
    final static int NFSPROC3_READ = 6;
    final static int NFSPROC3_WRITE = 7;
    final static int NFSPROC3_CREATE = 8;
    final static int NFSPROC3_MKDIR = 9;
    final static int NFSPROC3_SYMLINK = 10;
    final static int NFSPROC3_MKNOD = 11;
    final static int NFSPROC3_REMOVE = 12;
    final static int NFSPROC3_RMDIR = 13;
    final static int NFSPROC3_RENAME = 14;
    final static int NFSPROC3_LINK = 15;
    final static int NFSPROC3_READDIR = 16;
    final static int NFSPROC3_READDIRPLUS = 17;
    final static int NFSPROC3_FSSTAT = 18;
    final static int NFSPROC3_FSINFO = 19;
    final static int NFSPROC3_PATHCONF = 20;
    final static int NFSPROC3_COMMIT = 21;
    static final long ACCESS3_READ = 0x0001;
    static final long ACCESS3_LOOKUP = 0x0002;
    static final long ACCESS3_MODIFY = 0x0004;
    static final long ACCESS3_EXTEND = 0x0008;
    static final long ACCESS3_DELETE = 0x0010;
    static final long ACCESS3_EXECUTE = 0x0020;
    void disableSudo() throws IOException;
    void enableSudo(int uid, int gid);
    Credential getCredential();
    String getExportedPath();
    byte[] getRootFileHandle();
    int getPort();
    String getServer();
    F newFile(String path) throws IOException;
    Xdr nullCall() throws IOException;
    NfsGetAttrRequest makeGetAttrRequest(byte[] fileHandle) throws FileNotFoundException;
    NfsGetAttrResponse getAttr(NfsGetAttrRequest request) throws IOException;
    NfsGetAttrResponse wrapped_getAttr(NfsGetAttrRequest request) throws IOException;
    NfsSetAttrRequest makeSetAttrRequest(byte[] fileHandle, NfsSetAttributes attributes, NfsTime guardTime)
            throws FileNotFoundException;
    NfsSetAttrResponse setAttr(NfsSetAttrRequest request) throws IOException;
    NfsSetAttrResponse wrapped_setAttr(NfsSetAttrRequest request) throws IOException;
    NfsLookupRequest makeLookupRequest(byte[] fileHandle, String name) throws FileNotFoundException;
    NfsLookupResponse getLookup(NfsLookupRequest request) throws IOException;
    NfsLookupResponse wrapped_getLookup(NfsLookupRequest request) throws IOException;
    NfsAccessRequest makeAccessRequest(byte[] fileHandle, long accessToCheck) throws FileNotFoundException;
    NfsAccessResponse getAccess(NfsAccessRequest request) throws IOException;
    NfsAccessResponse wrapped_getAccess(NfsAccessRequest request) throws IOException;
    NfsReadlinkRequest makeReadlinkRequest(byte[] fileHandle) throws FileNotFoundException;
    NfsReadlinkResponse getReadlink(NfsReadlinkRequest request) throws IOException;
    NfsReadlinkResponse wrapped_getReadlink(NfsReadlinkRequest request) throws IOException;
    NfsReadRequest makeReadRequest(byte[] fileHandle, long offset, int size) throws FileNotFoundException;
    NfsReadResponse getRead(NfsReadRequest request, byte[] bytes, int position) throws IOException;
    NfsReadResponse wrapped_getRead(NfsReadRequest request, byte[] bytes, int position) throws IOException;
    NfsWriteRequest makeWriteRequest(byte[] fileHandle, long offset, List<ByteBuffer> payload, int syncType)
            throws FileNotFoundException;
    NfsWriteResponse sendWrite(NfsWriteRequest request) throws IOException;
    NfsWriteResponse wrapped_sendWrite(NfsWriteRequest request) throws IOException;
    NfsWriteResponse wrapped_sendWrite(NfsWriteRequest request, Long verifier) throws IOException;
    NfsCreateRequest makeCreateRequest(NfsCreateMode createMode, byte[] parentDirectoryFileHandle, String name,
            NfsSetAttributes attributes, byte[] verifier) throws FileNotFoundException;
    NfsCreateResponse sendCreate(NfsCreateRequest request) throws IOException;
    NfsCreateResponse wrapped_sendCreate(NfsCreateRequest request) throws IOException;
    NfsMkdirRequest makeMkdirRequest(byte[] fileHandle, String name, NfsSetAttributes attributes)
            throws FileNotFoundException;
    NfsMkdirResponse sendMkdir(NfsMkdirRequest request) throws IOException;
    NfsMkdirResponse wrapped_sendMkdir(NfsMkdirRequest request) throws IOException;
    NfsSymlinkRequest makeSymlinkRequest(String symbolicLinkData, byte[] fileHandle, String name,
            NfsSetAttributes attributes) throws FileNotFoundException;
    NfsSymlinkResponse sendSymlink(NfsSymlinkRequest request) throws IOException;
    NfsSymlinkResponse wrapped_sendSymlink(NfsSymlinkRequest request) throws IOException;
    NfsMknodRequest makeMknodRequest(byte[] fileHandle, String name, NfsType type, NfsSetAttributes attributes,
            long[] rdev) throws FileNotFoundException;
    NfsMknodResponse sendMknod(NfsMknodRequest request) throws IOException;
    NfsMknodResponse wrapped_sendMknod(NfsMknodRequest request) throws IOException;
    NfsRemoveRequest makeRemoveRequest(byte[] fileHandle, String name) throws FileNotFoundException;
    NfsRemoveResponse sendRemove(NfsRemoveRequest request) throws IOException;
    NfsRemoveResponse wrapped_sendRemove(NfsRemoveRequest request) throws IOException;
    NfsRmdirRequest makeRmdirRequest(byte[] fileHandle, String name) throws FileNotFoundException;
    NfsRmdirResponse sendRmdir(NfsRmdirRequest request) throws IOException;
    NfsRmdirResponse wrapped_sendRmdir(NfsRmdirRequest request) throws IOException;
    NfsRenameRequest makeRenameRequest(byte[] fileHandle, String name, byte[] toFileHandle, String toName)
            throws FileNotFoundException;
    NfsRenameResponse sendRename(NfsRenameRequest request) throws IOException;
    NfsRenameResponse wrapped_sendRename(NfsRenameRequest request) throws IOException;
    NfsLinkRequest makeLinkRequest(byte[] fileHandle, byte[] parentDirectoryFileHandle, String name)
            throws FileNotFoundException;
    NfsLinkResponse sendLink(NfsLinkRequest request) throws IOException;
    NfsLinkResponse wrapped_sendLink(NfsLinkRequest request) throws IOException;
    NfsReaddirRequest makeReaddirRequest(byte[] fileHandle, long cookie, long cookieverf, int count)
            throws FileNotFoundException;
    NfsReaddirResponse getReaddir(NfsReaddirRequest request) throws IOException;
    NfsReaddirResponse wrapped_getReaddir(NfsReaddirRequest request) throws IOException;
    NfsReaddirResponse wrapped_getReaddir(NfsReaddirRequest request, List<NfsDirectoryEntry> entries)
            throws IOException;
    NfsReaddirplusRequest makeReaddirplusRequest(byte[] fileHandle, long cookie, long cookieverf, int dircount,
            int maxcount) throws FileNotFoundException;
    NfsReaddirplusResponse getReaddirplus(NfsReaddirplusRequest request) throws IOException;
    NfsReaddirplusResponse wrapped_getReaddirplus(NfsReaddirplusRequest request) throws IOException;
    NfsReaddirplusResponse wrapped_getReaddirplus(NfsReaddirplusRequest request, List<NfsDirectoryPlusEntry> entries)
            throws IOException;
    NfsFsStat getNfsFsStat() throws IOException;
    NfsFsStatRequest makeFsStatRequest(byte[] fileHandle) throws FileNotFoundException;
    NfsFsStatRequest makeFsStatRequest() throws FileNotFoundException;
    NfsFsStatResponse getFsStat(NfsFsStatRequest request) throws IOException;
    NfsFsStatResponse wrapped_getFsStat(NfsFsStatRequest request) throws IOException;
    NfsFsInfo getNfsFsInfo() throws IOException;
    NfsFsInfoRequest makeFsInfoRequest(byte[] fileHandle) throws FileNotFoundException;
    NfsFsInfoRequest makeFsInfoRequest() throws FileNotFoundException;
    NfsFsInfoResponse getFsInfo(NfsFsInfoRequest request) throws IOException;
    NfsFsInfoResponse wrapped_getFsInfo(NfsFsInfoRequest request) throws IOException;
    NfsPathconfRequest makePathconfRequest(byte[] fileHandle) throws FileNotFoundException;
    NfsPathconfResponse getPathconf(NfsPathconfRequest request) throws IOException;
    NfsPathconfResponse wrapped_getPathconf(NfsPathconfRequest request) throws IOException;
    NfsCommitRequest makeCommitRequest(byte[] fileHandle, long offsetToCommit, int dataSizeToCommit)
            throws FileNotFoundException;
    NfsCommitResponse sendCommit(NfsCommitRequest request) throws IOException;
    NfsCommitResponse wrapped_sendCommit(NfsCommitRequest request) throws IOException;
}
