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

import com.emc.ecs.nfsclient.mount.MountException;
import com.emc.ecs.nfsclient.mount.MountRequest;
import com.emc.ecs.nfsclient.mount.MountResponse;
import com.emc.ecs.nfsclient.mount.MountStatus;
import com.emc.ecs.nfsclient.mount.UnmountRequest;
import com.emc.ecs.nfsclient.network.NetMgr;
import com.emc.ecs.nfsclient.nfs.Nfs;
import com.emc.ecs.nfsclient.nfs.NfsAccessRequest;
import com.emc.ecs.nfsclient.nfs.NfsCommitRequest;
import com.emc.ecs.nfsclient.nfs.NfsCreateMode;
import com.emc.ecs.nfsclient.nfs.NfsCreateRequest;
import com.emc.ecs.nfsclient.nfs.NfsDirectoryEntry;
import com.emc.ecs.nfsclient.nfs.NfsDirectoryPlusEntry;
import com.emc.ecs.nfsclient.nfs.NfsException;
import com.emc.ecs.nfsclient.nfs.NfsFsInfo;
import com.emc.ecs.nfsclient.nfs.NfsFsInfoRequest;
import com.emc.ecs.nfsclient.nfs.NfsFsStat;
import com.emc.ecs.nfsclient.nfs.NfsFsStatRequest;
import com.emc.ecs.nfsclient.nfs.NfsGetAttrRequest;
import com.emc.ecs.nfsclient.nfs.NfsLinkRequest;
import com.emc.ecs.nfsclient.nfs.NfsLookupRequest;
import com.emc.ecs.nfsclient.nfs.NfsMkdirRequest;
import com.emc.ecs.nfsclient.nfs.NfsMknodRequest;
import com.emc.ecs.nfsclient.nfs.NfsPathconfRequest;
import com.emc.ecs.nfsclient.nfs.NfsReadRequest;
import com.emc.ecs.nfsclient.nfs.NfsReaddirRequest;
import com.emc.ecs.nfsclient.nfs.NfsReaddirplusRequest;
import com.emc.ecs.nfsclient.nfs.NfsReadlinkRequest;
import com.emc.ecs.nfsclient.nfs.NfsRemoveRequest;
import com.emc.ecs.nfsclient.nfs.NfsRenameRequest;
import com.emc.ecs.nfsclient.nfs.NfsRequestBase;
import com.emc.ecs.nfsclient.nfs.NfsResponseBase;
import com.emc.ecs.nfsclient.nfs.NfsResponseHandler;
import com.emc.ecs.nfsclient.nfs.NfsRmdirRequest;
import com.emc.ecs.nfsclient.nfs.NfsSetAttrRequest;
import com.emc.ecs.nfsclient.nfs.NfsSetAttributes;
import com.emc.ecs.nfsclient.nfs.NfsStatus;
import com.emc.ecs.nfsclient.nfs.NfsSymlinkRequest;
import com.emc.ecs.nfsclient.nfs.NfsTime;
import com.emc.ecs.nfsclient.nfs.NfsType;
import com.emc.ecs.nfsclient.nfs.NfsWriteRequest;
import com.emc.ecs.nfsclient.nfs.io.Nfs3File;
import com.emc.ecs.nfsclient.portmap.Portmapper;
import com.emc.ecs.nfsclient.rpc.Credential;
import com.emc.ecs.nfsclient.rpc.CredentialUnix;
import com.emc.ecs.nfsclient.rpc.RejectStatus;
import com.emc.ecs.nfsclient.rpc.RpcException;
import com.emc.ecs.nfsclient.rpc.RpcRequest;
import com.emc.ecs.nfsclient.rpc.RpcResponse;
import com.emc.ecs.nfsclient.rpc.RpcResponseHandler;
import com.emc.ecs.nfsclient.rpc.RpcStatus;
import com.emc.ecs.nfsclient.rpc.RpcWrapper;
import com.emc.ecs.nfsclient.rpc.Xdr;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import org.apache.commons.lang3.NotImplementedException;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.mutable.MutableBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Nfs3 client operations implementation, as specified by RFC 1813
 * (https://tools.ietf.org/html/rfc1813)
 *
 * @author seibed
 */
public class Nfs3 implements Nfs<Nfs3File> {

  /** The usual logger. */
  private static final Logger LOG = LoggerFactory.getLogger(Nfs3.class);

  // NFS version number for RPC use
  /** The NFS version number. */
  public static final int VERSION = 3;

  /** The timeout in seconds. */
  public static final int NFS_TIMEOUT = 10; // unit: seconds

  /** The maximum size in bytes. 8K is enough for all requests except for data writing. */
  public static final int MAXIMUM_NFS_REQUEST_SIZE = 8 * 1024;

  /** The remote NFS server name. */
  private final String _server;

  /** The remote NFS server port, configured explicitly or obtained from Portmap. */
  private volatile int _port = 0;

  private final int _configuredNfsPort;
  private final int _configuredMountPort;
  private volatile int _mountPort;

  /** The exported path handled by this client. */
  private final String _exportedPath;

  /** The root file handle for the exported path, obtained by a MOUNT call. */
  private volatile byte[] _rootFileHandle = null;

  /** Maximum NFS call retries, used in the wrapped method calls. */
  private final int _maximumRetries;

  /** The default wait time between retries in milliseconds. */
  private static final int DEFAULT_WAIT_TIME_MILLIS = 1000;

  /** The wait time between retries in milliseconds. */
  private final int _retryWait = DEFAULT_WAIT_TIME_MILLIS;

  /** Lock for calls to determine the port and root file handle. */
  private final Lock _prepareLock = new ReentrantLock();

  /** Credential used for RPC calls. */
  private Credential _credential = new CredentialUnix();

  /** The helper instance used to wrap RPC calls. */
  private final RpcWrapper<NfsRequestBase, NfsResponseBase> _rpcWrapper;

  /** Timeout for MOUNT calls in seconds. */
  private static final int MOUNT_RPC_TIMEOUT = 10; // seconds

  /** Maximum retries for MOUNT calls. */
  private static final int MOUNT_MAX_RETRIES = 2;

  /** maximum mount request size (bytes) */
  private static final int MOUNT_MAX_REQUEST_SIZE = 8 * 1024;

  /** Convenience constructor */
  public Nfs3(String server, String exportedPath, byte[] rootFileHandle, int maximumRetries)
      throws IOException {
    this(server, exportedPath, rootFileHandle, null, maximumRetries);
  }

  /** Convenience constructor */
  public Nfs3(String server, String exportedPath, Credential credential, int maximumRetries)
      throws IOException {
    this(server, exportedPath, null, credential, maximumRetries);
  }

  /**
   * Convenience constructor
   *
   * @param absolutePath The fully qualified path to the export, e.g., 10.32.172.64:/store.
   */
  public Nfs3(String absolutePath, int uid, int gid, int maximumRetries) throws IOException {
    this(absolutePath, new CredentialUnix(uid, gid, null), maximumRetries);
  }

  /**
   * Convenience constructor
   *
   * @param absolutePath The fully qualified path to the export, e.g., 10.32.172.64:/store.
   */
  public Nfs3(String absolutePath, Credential credential, int maximumRetries) throws IOException {
    this(getServer(absolutePath), getExportedPath(absolutePath), credential, maximumRetries);
  }

  /**
   * Full constructor.
   *
   * @param server The NFS server name or IP address.
   * @param exportedPath The exported path
   */
  public Nfs3(
      String server,
      String exportedPath,
      byte[] rootFileHandle,
      Credential credential,
      int maximumRetries)
      throws IOException {
    this(server, exportedPath, rootFileHandle, credential, maximumRetries, 0, 0);
  }

  /**
   * Create a client with independently configurable NFS and MOUNT TCP destination ports.
   *
   * @param server the NFS server name or IP address (without a port)
   * @param exportedPath the exported path
   * @param credential RPC credential, or null for the default UNIX credential
   * @param maximumRetries positive maximum number of NFS call attempts
   * @param nfsPort NFS port (1-65535), or 0 to discover through portmapper
   * @param mountPort MOUNT port (1-65535), or 0 to discover through portmapper
   * @throws IOException if service discovery or mounting fails
   * @throws IllegalArgumentException if a port is outside 0-65535
   */
  public Nfs3(
      String server,
      String exportedPath,
      Credential credential,
      int maximumRetries,
      int nfsPort,
      int mountPort)
      throws IOException {
    this(server, exportedPath, null, credential, maximumRetries, nfsPort, mountPort);
  }

  /**
   * Full constructor with optional explicit TCP destination ports. A nonzero port bypasses
   * portmapper for that service, including when the root handle is refreshed. Supplying a root
   * handle skips MOUNT; with an explicit NFS port it also skips all portmapper discovery. Existing
   * constructors are equivalent to specifying 0 for both ports.
   *
   * @param server the NFS server name or IP address (without a port)
   * @param exportedPath the exported path
   * @param rootFileHandle root file handle, or null to obtain it using MOUNT
   * @param credential RPC credential, or null for the default UNIX credential
   * @param maximumRetries positive maximum number of NFS call attempts
   * @param nfsPort NFS port (1-65535), or 0 to discover through portmapper
   * @param mountPort MOUNT port (1-65535), or 0 to discover through portmapper
   * @throws IOException if service discovery or mounting fails
   * @throws IllegalArgumentException if a port is outside 0-65535
   */
  public Nfs3(
      String server,
      String exportedPath,
      byte[] rootFileHandle,
      Credential credential,
      int maximumRetries,
      int nfsPort,
      int mountPort)
      throws IOException {
    checkForBlank(server, "server");
    checkForBlank(exportedPath, "exportedPath");
    checkPort(nfsPort, "nfsPort");
    checkPort(mountPort, "mountPort");
    if (maximumRetries <= 0) {
      throw new IllegalArgumentException("maxRetry must be positive.");
    }

    _server = server;
    _exportedPath = exportedPath;
    _maximumRetries = maximumRetries;
    _configuredNfsPort = nfsPort;
    _configuredMountPort = mountPort;
    _mountPort = mountPort;

    if (credential != null) {
      _credential = credential;
    }
    _rpcWrapper =
        new RpcWrapper<NfsRequestBase, NfsResponseBase>(
            _server, _port, _retryWait, _maximumRetries, MAXIMUM_NFS_REQUEST_SIZE, NFS_TIMEOUT);

    if (rootFileHandle == null) {
      prepareRootFhAndNfsPort();
    } else {
      _rootFileHandle = rootFileHandle.clone();
      _port = resolveNfsPort();
      _rpcWrapper.setPort(_port);
    }
  }

  private static void checkPort(int port, String name) {
    if (port < 0 || port > 65535) {
      throw new IllegalArgumentException(
          name + " must be between 0 and 65535 (0 means automatic discovery).");
    }
  }

  private int resolveNfsPort() throws IOException {
    return _configuredNfsPort == 0 ? getNfsPortFromServer() : _configuredNfsPort;
  }

  /**
   * Convenience method to check String parameters that cannot be blank.
   *
   * @param value The parameter value.
   * @param name The parameter name, for exception messages.
   */
  private void checkForBlank(String value, String name) {
    if (StringUtils.isBlank(value)) {
      throw new IllegalArgumentException(name + " cannot be empty");
    }
  }

  /**
   * Returns the server.
   *
   * @param absolutePath The fully qualified path to the export, e.g., 10.32.172.64:/store.
   * @return The server.
   */
  private static String getServer(String absolutePath) {
    int index = absolutePath.indexOf(":");
    return absolutePath.substring(0, Math.max(index, 0));
  }

  /**
   * Returns the exported path.
   *
   * @param absolutePath The fully qualified path to the export, e.g., 10.32.172.64:/store.
   * @return The exported path.
   */
  private static String getExportedPath(String absolutePath) {
    int index = absolutePath.indexOf(":");
    return absolutePath.substring(index + 1);
  }

  /** Query the port and root file handle for NFS server. */
  private void prepareRootFhAndNfsPort() throws IOException {

    if (!_prepareLock.tryLock()) {
      return;
    }

    try {
      LOG.debug(
          "Discovering NFSv{} services: server={}, export={}", VERSION, _server, _exportedPath);
      _port = resolveNfsPort();
      _rpcWrapper.setPort(_port);
      _rootFileHandle = lookupRootHandle().clone();
    } finally {
      _prepareLock.unlock();
    }
  }

  /**
   * Returns the root handle.
   *
   * @return the root handle
   */
  byte[] lookupRootHandle() throws IOException {
    int portOfMountService =
        _configuredMountPort == 0 ? getMountPortFromServer() : _configuredMountPort;
    _mountPort = portOfMountService;

    MountResponse response = null;
    MountRequest request = new MountRequest(VERSION, _exportedPath, _credential);
    boolean usePrivilegedPort = false;
    for (int i = 0; i < MOUNT_MAX_RETRIES; ++i) {
      try {
        Xdr mountXdr = new Xdr(MOUNT_MAX_REQUEST_SIZE);
        request.marshalling(mountXdr);
        response = new MountResponse(VERSION);
        LOG.info(
            "Mounting NFSv{} export: server={}, mountPort={}, export={}, attempt={}/{}, privilegedPort={}",
            VERSION,
            _server,
            portOfMountService,
            _exportedPath,
            i + 1,
            MOUNT_MAX_RETRIES,
            usePrivilegedPort);
        response.unmarshalling(
            NetMgr.getInstance()
                .sendAndWait(
                    _server, portOfMountService, usePrivilegedPort, mountXdr, MOUNT_RPC_TIMEOUT));
        int status = response.getMountStatus();
        if (status != MountStatus.MNT3_OK.getValue()) {
          String guidance = "";
          if (status == MountStatus.MNT3ERR_ACCES.getValue()
              || status == MountStatus.MNT3ERR_PERM.getValue()) {
            guidance =
                " Permission denied: check the server export's client allowlist, UID/GID permissions,"
                    + " and whether it requires a privileged client source port (<1024)."
                    + " Binding such a port requires local OS permission; AUTH_SYS UID 0 does not grant it.";
          } else if (status == MountStatus.MNT3ERR_NOENT.getValue()
              || status == MountStatus.MNT3ERR_NOTDIR.getValue()) {
            guidance =
                " Check that the exported path exists and is an exported directory on the server.";
          } else if (status == MountStatus.MNT3ERR_NOTSUPP.getValue()) {
            guidance = " Check that the server enables NFSv3 and the mount v3 service.";
          }
          String msg =
              String.format(
                  "MOUNT failed: server=%s, mountPort=%d, export=%s, nfsVersion=%d,"
                      + " attempt=%d/%d, privilegedPort=%s, status=%s.%s",
                  _server,
                  portOfMountService,
                  _exportedPath,
                  VERSION,
                  i + 1,
                  MOUNT_MAX_RETRIES,
                  usePrivilegedPort,
                  MountStatus.fromValue(status),
                  guidance);
          LOG.warn("{}", msg);
          throw new MountException(MountStatus.fromValue(status), msg);
        }
        LOG.debug(
            "Mounted NFS export: server={}, mountPort={}, export={}, xid={}",
            _server,
            portOfMountService,
            _exportedPath,
            mountXdr.getXid());
        break;
      } catch (RpcException e) {
        usePrivilegedPort = handleRpcException(e, i, portOfMountService, usePrivilegedPort);
      }
    }

    // unmount it, so the server knows we are not holding on to it
    UnmountRequest unmountRequest = new UnmountRequest(VERSION, _exportedPath, _credential);
    for (int i = 0; i < MOUNT_MAX_RETRIES; ++i) {
      try {
        Xdr unmountXdr = new Xdr(MOUNT_MAX_REQUEST_SIZE);
        unmountRequest.marshalling(unmountXdr);
        // UMNT has no application body, but its RPC reply still has a status to validate.
        // If we mounted with a privileged port, use one to unmount.
        new RpcResponse()
            .unmarshalling(
                NetMgr.getInstance()
                    .sendAndWait(
                        _server,
                        portOfMountService,
                        usePrivilegedPort,
                        unmountXdr,
                        MOUNT_RPC_TIMEOUT));
        LOG.debug(
            "Removed mount registration: server={}, mountPort={}, export={}",
            _server,
            portOfMountService,
            _exportedPath);
        break;
      } catch (RpcException e) {
        LOG.warn(
            "UNMOUNT registration cleanup failed: server={}, mountPort={}, export={},"
                + " attempt={}/{}, privilegedPort={}, status={}, mount remains usable: {}",
            _server,
            portOfMountService,
            _exportedPath,
            i + 1,
            MOUNT_MAX_RETRIES,
            usePrivilegedPort,
            e.getStatus(),
            e.getMessage(),
            e);
      }
    }

    // If this gets here, the response cannot be null.
    // log the security mode that NFS supports, though we just
    // support AUTH_UNIX now
    if (LOG.isDebugEnabled()) {
      int[] authenticationFlavors = response.getAuthenticationFlavors();
      LOG.debug(
          "nfs server {}:{} supports auth mode {}",
          _server,
          _exportedPath,
          Arrays.toString(authenticationFlavors));
    }

    return response.getRootFileHandle();
  }

  /**
   * Decide whether to retry or throw an exception
   *
   * @param e The exception.
   * @param attemptNumber The number of attempts so far.
   * @return
   *     <ul>
   *       <li><code>true</code> if there was an authentication failure and privileged ports should
   *           be tried,
   *       <li><code>false</code> otherwise.
   *     </ul>
   */
  private boolean handleRpcException(
      RpcException e, int attemptNumber, int mountPort, boolean usePrivilegedPort)
      throws IOException {
    boolean tryPrivilegedPort = e.getStatus().equals(RejectStatus.AUTH_ERROR);
    boolean networkError = e.getStatus().equals(RpcStatus.NETWORK_ERROR);
    boolean retry =
        (tryPrivilegedPort || networkError) && ((attemptNumber + 1) < MOUNT_MAX_RETRIES);
    if (!retry) {
      String msg =
          String.format(
              "MOUNT RPC failed: server=%s, mountPort=%d, export=%s, nfsVersion=%d,"
                  + " attempt=%d/%d, privilegedPort=%s, status=%s: %s",
              _server,
              mountPort,
              _exportedPath,
              VERSION,
              attemptNumber + 1,
              MOUNT_MAX_RETRIES,
              usePrivilegedPort,
              e.getStatus(),
              e.getMessage());
      LOG.warn("{}", msg, e);
      throw new MountException(MountStatus.MNT3ERR_IO, msg, e);
    }
    LOG.warn(
        "MOUNT RPC failed; retrying: server={}, mountPort={}, export={}, attempt={}/{}, status={},"
            + " nextPrivilegedPort={}: {}",
        _server,
        mountPort,
        _exportedPath,
        attemptNumber + 1,
        MOUNT_MAX_RETRIES,
        e.getStatus(),
        tryPrivilegedPort,
        e.getMessage(),
        e);
    if (tryPrivilegedPort) {
      LOG.info(
          "Retrying MOUNT using a privileged client source port (<1024); local OS permission is required:"
              + " server={}, export={}",
          _server,
          _exportedPath);
    }
    return tryPrivilegedPort;
  }

  /**
   * Returns the NFS Server RPC port number.
   *
   * @return The NFS Server RPC port number.
   */
  int getNfsPortFromServer() throws IOException {
    return Portmapper.queryPortFromPortMap(RPC_PROGRAM, VERSION, _server);
  }

  int getMountPortFromServer() throws IOException {
    return Portmapper.queryPortFromPortMap(MOUNTPROG, VERSION, _server);
  }

  /** {@inheritDoc} */
  @Override
  public void disableSudo() throws IOException {
    _credential = new CredentialUnix();
  }

  /** {@inheritDoc} */
  @Override
  public void enableSudo(int uid, int gid) {
    _credential = new CredentialUnix(uid, gid, null);
  }

  /** {@inheritDoc} */
  @Override
  public Credential getCredential() {
    return _credential;
  }

  /** {@inheritDoc} */
  @Override
  public String getExportedPath() {
    return _exportedPath;
  }

  /** {@inheritDoc} */
  @Override
  public Nfs3File newFile(String path) throws IOException {
    return new Nfs3File(this, path);
  }

  /** {@inheritDoc} */
  @Override
  public int getPort() {
    return _port;
  }

  @Override
  public int getMountPort() {
    return _mountPort;
  }

  /** {@inheritDoc} */
  @Override
  public byte[] getRootFileHandle() {
    return _rootFileHandle.clone();
  }

  /** {@inheritDoc} */
  @Override
  public String getServer() {
    return _server;
  }

  /** {@inheritDoc} */
  @Override
  public Xdr nullCall() throws IOException {
    Xdr xdr = new Xdr(MAXIMUM_NFS_REQUEST_SIZE);
    new RpcRequest(RPC_PROGRAM, VERSION, NFSPROC3_NULL, _credential) {

      /* (non-Javadoc)
       * @see com.emc.ecs.nfsclient.rpc.RpcRequest#getErrorMessage()
       */
      @Override
      public String getErrorMessage() {
        throw new NotImplementedException("This should never be used for a NULL call.");
      }
    }.marshalling(xdr);
    return _rpcWrapper.callRpc(
        _rpcWrapper.chooseIP(_server.getBytes(RpcRequest.CHARSET)), xdr, false);
  }

  /** {@inheritDoc} */
  @Override
  public Nfs3GetAttrRequest makeGetAttrRequest(byte[] fileHandle) throws FileNotFoundException {
    return new Nfs3GetAttrRequest(fileHandle, _credential);
  }

  /** {@inheritDoc} */
  @Override
  public Nfs3GetAttrResponse getAttr(NfsGetAttrRequest request) throws IOException {
    Nfs3GetAttrResponse response = new Nfs3GetAttrResponse();
    _rpcWrapper.callRpcNaked(request, response);
    return response;
  }

  /** {@inheritDoc} */
  @Override
  public Nfs3GetAttrResponse wrapped_getAttr(NfsGetAttrRequest request) throws IOException {
    NfsResponseHandler<Nfs3GetAttrResponse> responseHandler =
        new NfsResponseHandler<Nfs3GetAttrResponse>() {

          /* (non-Javadoc)
           * @see com.emc.ecs.nfsclient.rpc.RpcResponseHandler#makeNewResponse()
           */
          @Override
          protected Nfs3GetAttrResponse makeNewResponse() {
            return new Nfs3GetAttrResponse();
          }
        };
    _rpcWrapper.callRpcWrapped(request, responseHandler);
    return responseHandler.getResponse();
  }

  /** {@inheritDoc} */
  @Override
  public NfsSetAttrRequest makeSetAttrRequest(
      byte[] fileHandle, NfsSetAttributes attributes, NfsTime guardTime)
      throws FileNotFoundException {
    return new Nfs3SetAttrRequest(fileHandle, attributes, guardTime, _credential);
  }

  /** {@inheritDoc} */
  @Override
  public Nfs3SetAttrResponse setAttr(NfsSetAttrRequest request) throws IOException {
    Nfs3SetAttrResponse response = new Nfs3SetAttrResponse();
    _rpcWrapper.callRpcNaked(request, response);
    return response;
  }

  /** {@inheritDoc} */
  @Override
  public Nfs3SetAttrResponse wrapped_setAttr(NfsSetAttrRequest request) throws IOException {
    NfsResponseHandler<Nfs3SetAttrResponse> responseHandler =
        new NfsResponseHandler<Nfs3SetAttrResponse>() {

          /* (non-Javadoc)
           * @see com.emc.ecs.nfsclient.rpc.RpcResponseHandler#makeNewResponse()
           */
          @Override
          protected Nfs3SetAttrResponse makeNewResponse() {
            return new Nfs3SetAttrResponse();
          }
        };
    _rpcWrapper.callRpcWrapped(request, responseHandler);
    return responseHandler.getResponse();
  }

  /** {@inheritDoc} */
  @Override
  public Nfs3LookupRequest makeLookupRequest(byte[] fileHandle, String name)
      throws FileNotFoundException {
    return new Nfs3LookupRequest(fileHandle, name, _credential);
  }

  /** {@inheritDoc} */
  @Override
  public Nfs3LookupResponse getLookup(NfsLookupRequest request) throws IOException {
    Nfs3LookupResponse response = new Nfs3LookupResponse();
    _rpcWrapper.callRpcNaked(request, response);
    return response;
  }

  /** {@inheritDoc} */
  @Override
  public Nfs3LookupResponse wrapped_getLookup(NfsLookupRequest request) throws IOException {
    RpcResponseHandler<Nfs3LookupResponse> responseHandler =
        new NfsResponseHandler<Nfs3LookupResponse>() {

          /* (non-Javadoc)
           * @see com.emc.ecs.nfsclient.rpc.RpcResponseHandler#makeNewResponse()
           */
          @Override
          protected Nfs3LookupResponse makeNewResponse() {
            return new Nfs3LookupResponse();
          }

          /* (non-Javadoc)
           * @see com.emc.ecs.nfsclient.nfs.NfsResponseHandler#checkResponse(com.emc.ecs.nfsclient.rpc.RpcRequest)
           */
          @Override
          public void checkResponse(RpcRequest request) throws IOException {
            // If the root file handle is bad, we should mount again.
            if ((getResponse().getState() == NfsStatus.NFS3ERR_BADHANDLE.getValue())
                && Arrays.equals(((NfsLookupRequest) request).getFileHandle(), _rootFileHandle)) {
              prepareRootFhAndNfsPort();
            }
            super.checkResponse(request);
          }
        };
    _rpcWrapper.callRpcWrapped(request, responseHandler);
    return responseHandler.getResponse();
  }

  /** {@inheritDoc} */
  @Override
  public Nfs3AccessRequest makeAccessRequest(byte[] fileHandle, long accessToCheck)
      throws FileNotFoundException {
    return new Nfs3AccessRequest(fileHandle, accessToCheck, _credential);
  }

  /** {@inheritDoc} */
  @Override
  public Nfs3AccessResponse getAccess(NfsAccessRequest request) throws IOException {
    Nfs3AccessResponse response = new Nfs3AccessResponse();
    _rpcWrapper.callRpcNaked(request, response);
    return response;
  }

  /** {@inheritDoc} */
  @Override
  public Nfs3AccessResponse wrapped_getAccess(NfsAccessRequest request) throws IOException {
    NfsResponseHandler<Nfs3AccessResponse> responseHandler =
        new NfsResponseHandler<Nfs3AccessResponse>() {

          /* (non-Javadoc)
           * @see com.emc.ecs.nfsclient.rpc.RpcResponseHandler#makeNewResponse()
           */
          @Override
          protected Nfs3AccessResponse makeNewResponse() {
            return new Nfs3AccessResponse();
          }
        };
    _rpcWrapper.callRpcWrapped(request, responseHandler);
    return responseHandler.getResponse();
  }

  /** {@inheritDoc} */
  @Override
  public Nfs3ReadlinkRequest makeReadlinkRequest(byte[] fileHandle) throws FileNotFoundException {
    return new Nfs3ReadlinkRequest(fileHandle, _credential);
  }

  /** {@inheritDoc} */
  @Override
  public Nfs3ReadlinkResponse getReadlink(NfsReadlinkRequest request) throws IOException {
    Nfs3ReadlinkResponse response = new Nfs3ReadlinkResponse();
    _rpcWrapper.callRpcNaked(request, response);
    return response;
  }

  /** {@inheritDoc} */
  @Override
  public Nfs3ReadlinkResponse wrapped_getReadlink(NfsReadlinkRequest request) throws IOException {
    NfsResponseHandler<Nfs3ReadlinkResponse> responseHandler =
        new NfsResponseHandler<Nfs3ReadlinkResponse>() {

          /* (non-Javadoc)
           * @see com.emc.ecs.nfsclient.rpc.RpcResponseHandler#makeNewResponse()
           */
          @Override
          protected Nfs3ReadlinkResponse makeNewResponse() {
            return new Nfs3ReadlinkResponse();
          }
        };

    _rpcWrapper.callRpcWrapped(request, responseHandler);
    return responseHandler.getResponse();
  }

  /** {@inheritDoc} */
  @Override
  public Nfs3ReadRequest makeReadRequest(byte[] fileHandle, long offset, int size)
      throws FileNotFoundException {
    return new Nfs3ReadRequest(fileHandle, offset, size, _credential);
  }

  /** {@inheritDoc} */
  @Override
  public Nfs3ReadResponse getRead(NfsReadRequest request, byte[] bytes, int position)
      throws IOException {
    Nfs3ReadResponse response = new Nfs3ReadResponse(bytes, position);
    _rpcWrapper.callRpcNaked(request, response);
    return response;
  }

  /** {@inheritDoc} */
  @Override
  public Nfs3ReadResponse wrapped_getRead(
      NfsReadRequest request, final byte[] bytes, final int position) throws IOException {
    NfsResponseHandler<Nfs3ReadResponse> responseHandler =
        new NfsResponseHandler<Nfs3ReadResponse>() {

          /* (non-Javadoc)
           * @see com.emc.ecs.nfsclient.rpc.RpcResponseHandler#makeNewResponse()
           */
          @Override
          protected Nfs3ReadResponse makeNewResponse() {
            return new Nfs3ReadResponse(bytes, position);
          }
        };

    _rpcWrapper.callRpcWrapped(request, responseHandler);
    return responseHandler.getResponse();
  }

  /** {@inheritDoc} */
  @Override
  public Nfs3WriteRequest makeWriteRequest(
      byte[] fileHandle, long offset, List<ByteBuffer> payload, int syncType)
      throws FileNotFoundException {
    return new Nfs3WriteRequest(fileHandle, offset, payload, syncType, _credential);
  }

  /** {@inheritDoc} */
  @Override
  public Nfs3WriteResponse sendWrite(NfsWriteRequest request) throws IOException {
    Nfs3WriteResponse response = new Nfs3WriteResponse();
    _rpcWrapper.callRpcNaked(request, response);
    return response;
  }

  /** {@inheritDoc} */
  @Override
  public Nfs3WriteResponse wrapped_sendWrite(NfsWriteRequest request) throws IOException {
    // for async write, all the writes and commit should be sent to
    // the same NFS server
    String ip = request.isSync() ? _rpcWrapper.chooseIP(request.getIpKey()) : _server;
    NfsResponseHandler<Nfs3WriteResponse> responseHandler =
        new NfsResponseHandler<Nfs3WriteResponse>() {

          /* (non-Javadoc)
           * @see com.emc.ecs.nfsclient.rpc.RpcResponseHandler#makeNewResponse()
           */
          @Override
          protected Nfs3WriteResponse makeNewResponse() {
            return new Nfs3WriteResponse();
          }
        };
    _rpcWrapper.callRpcWrapped(request, responseHandler, ip);
    return responseHandler.getResponse();
  }

  /** {@inheritDoc} */
  @Override
  public Nfs3WriteResponse wrapped_sendWrite(NfsWriteRequest request, final Long verifier)
      throws IOException {
    // for async write, all the writes and commit should be sent to
    // the same NFS server
    String ip = request.isSync() ? _rpcWrapper.chooseIP(request.getIpKey()) : _server;
    NfsResponseHandler<Nfs3WriteResponse> responseHandler =
        new NfsResponseHandler<Nfs3WriteResponse>() {

          @Override
          protected Nfs3WriteResponse makeNewResponse() {
            return new Nfs3WriteResponse();
          }

          /* (non-Javadoc)
           * @see com.emc.ecs.nfsclient.nfs.NfsResponseHandler#checkResponse(com.emc.ecs.nfsclient.rpc.RpcRequest)
           */
          @Override
          public void checkResponse(RpcRequest request) throws IOException {
            super.checkResponse(request);
            if (!((NfsWriteRequest) request).isSync()
                && verifier != null
                && getResponse().getVerf() != verifier) {
              throw new NfsException(
                  NfsStatus.NFS3ERR_SERVERFAULT,
                  "WRITE verifier changed (possible server restart): server="
                      + _server
                      + ", port="
                      + _port
                      + ", expectedVerifier="
                      + verifier
                      + ", actualVerifier="
                      + getResponse().getVerf()
                      + ", request="
                      + request);
            }
          }
        };
    _rpcWrapper.callRpcWrapped(request, responseHandler, ip);
    return responseHandler.getResponse();
  }

  /** {@inheritDoc} */
  @Override
  public Nfs3CreateRequest makeCreateRequest(
      NfsCreateMode createMode,
      byte[] parentDirectoryFileHandle,
      String name,
      NfsSetAttributes attributes,
      byte[] verifier)
      throws FileNotFoundException {
    return new Nfs3CreateRequest(
        createMode, parentDirectoryFileHandle, name, attributes, verifier, _credential);
  }

  /** {@inheritDoc} */
  @Override
  public Nfs3CreateResponse sendCreate(NfsCreateRequest request) throws IOException {
    Nfs3CreateResponse response = new Nfs3CreateResponse();
    _rpcWrapper.callRpcNaked(request, response);
    return response;
  }

  /** {@inheritDoc} */
  @Override
  public Nfs3CreateResponse wrapped_sendCreate(NfsCreateRequest request) throws IOException {
    NfsResponseHandler<Nfs3CreateResponse> responseHandler =
        new NfsResponseHandler<Nfs3CreateResponse>() {

          /* (non-Javadoc)
           * @see com.emc.ecs.nfsclient.rpc.RpcResponseHandler#makeNewResponse()
           */
          @Override
          protected Nfs3CreateResponse makeNewResponse() {
            return new Nfs3CreateResponse();
          }
        };
    _rpcWrapper.callRpcWrapped(request, responseHandler);
    return responseHandler.getResponse();
  }

  /** {@inheritDoc} */
  @Override
  public Nfs3MkdirRequest makeMkdirRequest(
      byte[] parentDirectoryFileHandle, String name, NfsSetAttributes attributes)
      throws FileNotFoundException {
    return new Nfs3MkdirRequest(parentDirectoryFileHandle, name, attributes, _credential);
  }

  /** {@inheritDoc} */
  @Override
  public Nfs3MkdirResponse sendMkdir(NfsMkdirRequest request) throws IOException {
    Nfs3MkdirResponse response = new Nfs3MkdirResponse();
    _rpcWrapper.callRpcNaked(request, response);
    return response;
  }

  /** {@inheritDoc} */
  @Override
  public Nfs3MkdirResponse wrapped_sendMkdir(NfsMkdirRequest request) throws IOException {
    RpcResponseHandler<Nfs3MkdirResponse> responseHandler =
        new NfsResponseHandler<Nfs3MkdirResponse>() {

          /* (non-Javadoc)
           * @see com.emc.ecs.nfsclient.rpc.RpcResponseHandler#makeNewResponse()
           */
          @Override
          protected Nfs3MkdirResponse makeNewResponse() {
            return new Nfs3MkdirResponse();
          }
        };
    _rpcWrapper.callRpcWrapped(request, responseHandler);
    return responseHandler.getResponse();
  }

  /** {@inheritDoc} */
  @Override
  public Nfs3SymlinkRequest makeSymlinkRequest(
      String symbolicLinkData,
      byte[] parentDirectoryFileHandle,
      String name,
      NfsSetAttributes attributes)
      throws FileNotFoundException {
    return new Nfs3SymlinkRequest(
        symbolicLinkData, parentDirectoryFileHandle, name, attributes, _credential);
  }

  /** {@inheritDoc} */
  @Override
  public Nfs3SymlinkResponse sendSymlink(NfsSymlinkRequest request) throws IOException {
    Nfs3SymlinkResponse response = new Nfs3SymlinkResponse();
    _rpcWrapper.callRpcNaked(request, response);
    return response;
  }

  /** {@inheritDoc} */
  @Override
  public Nfs3SymlinkResponse wrapped_sendSymlink(NfsSymlinkRequest request) throws IOException {
    RpcResponseHandler<Nfs3SymlinkResponse> responseHandler =
        new NfsResponseHandler<Nfs3SymlinkResponse>() {

          /* (non-Javadoc)
           * @see com.emc.ecs.nfsclient.rpc.RpcResponseHandler#makeNewResponse()
           */
          @Override
          protected Nfs3SymlinkResponse makeNewResponse() {
            return new Nfs3SymlinkResponse();
          }
        };
    _rpcWrapper.callRpcWrapped(request, responseHandler);
    return responseHandler.getResponse();
  }

  /** {@inheritDoc} */
  @Override
  public Nfs3MknodRequest makeMknodRequest(
      byte[] parentDirectoryFileHandle,
      String name,
      NfsType type,
      NfsSetAttributes attributes,
      long[] rdev)
      throws FileNotFoundException {
    return new Nfs3MknodRequest(
        parentDirectoryFileHandle, name, type, attributes, rdev, _credential);
  }

  /** {@inheritDoc} */
  @Override
  public Nfs3MknodResponse sendMknod(NfsMknodRequest request) throws IOException {
    Nfs3MknodResponse response = new Nfs3MknodResponse();
    _rpcWrapper.callRpcNaked(request, response);
    return response;
  }

  /** {@inheritDoc} */
  @Override
  public Nfs3MknodResponse wrapped_sendMknod(NfsMknodRequest request) throws IOException {
    RpcResponseHandler<Nfs3MknodResponse> responseHandler =
        new NfsResponseHandler<Nfs3MknodResponse>() {

          /* (non-Javadoc)
           * @see com.emc.ecs.nfsclient.rpc.RpcResponseHandler#makeNewResponse()
           */
          @Override
          protected Nfs3MknodResponse makeNewResponse() {
            return new Nfs3MknodResponse();
          }
        };
    _rpcWrapper.callRpcWrapped(request, responseHandler);
    return responseHandler.getResponse();
  }

  /** {@inheritDoc} */
  @Override
  public Nfs3RemoveRequest makeRemoveRequest(byte[] parentDirectoryFileHandle, String name)
      throws FileNotFoundException {
    return new Nfs3RemoveRequest(parentDirectoryFileHandle, name, _credential);
  }

  /** {@inheritDoc} */
  @Override
  public Nfs3RemoveResponse sendRemove(NfsRemoveRequest request) throws IOException {
    Nfs3RemoveResponse response = new Nfs3RemoveResponse();
    _rpcWrapper.callRpcNaked(request, response);
    return response;
  }

  /** {@inheritDoc} */
  @Override
  public Nfs3RemoveResponse wrapped_sendRemove(NfsRemoveRequest request) throws IOException {
    RpcResponseHandler<Nfs3RemoveResponse> responseHandler =
        new NfsResponseHandler<Nfs3RemoveResponse>() {

          /* (non-Javadoc)
           * @see com.emc.ecs.nfsclient.rpc.RpcResponseHandler#makeNewResponse()
           */
          @Override
          protected Nfs3RemoveResponse makeNewResponse() {
            return new Nfs3RemoveResponse();
          }
        };
    _rpcWrapper.callRpcWrapped(request, responseHandler);
    return responseHandler.getResponse();
  }

  /** {@inheritDoc} */
  @Override
  public Nfs3RmdirRequest makeRmdirRequest(byte[] parentDirectoryFileHandle, String name)
      throws FileNotFoundException {
    return new Nfs3RmdirRequest(parentDirectoryFileHandle, name, _credential);
  }

  /** {@inheritDoc} */
  @Override
  public Nfs3RmdirResponse sendRmdir(NfsRmdirRequest request) throws IOException {
    Nfs3RmdirResponse response = new Nfs3RmdirResponse();
    _rpcWrapper.callRpcNaked(request, response);
    return response;
  }

  /** {@inheritDoc} */
  @Override
  public Nfs3RmdirResponse wrapped_sendRmdir(NfsRmdirRequest request) throws IOException {
    RpcResponseHandler<Nfs3RmdirResponse> responseHandler =
        new NfsResponseHandler<Nfs3RmdirResponse>() {

          /* (non-Javadoc)
           * @see com.emc.ecs.nfsclient.rpc.RpcResponseHandler#makeNewResponse()
           */
          @Override
          protected Nfs3RmdirResponse makeNewResponse() {
            return new Nfs3RmdirResponse();
          }
        };
    _rpcWrapper.callRpcWrapped(request, responseHandler);
    return responseHandler.getResponse();
  }

  /** {@inheritDoc} */
  @Override
  public Nfs3RenameRequest makeRenameRequest(
      byte[] fromDirectoryFileHandle, String fromName, byte[] toDirectoryFileHandle, String toName)
      throws FileNotFoundException {
    return new Nfs3RenameRequest(
        fromDirectoryFileHandle, fromName, toDirectoryFileHandle, toName, _credential);
  }

  /** {@inheritDoc} */
  @Override
  public Nfs3RenameResponse sendRename(NfsRenameRequest request) throws IOException {
    Nfs3RenameResponse response = new Nfs3RenameResponse();
    _rpcWrapper.callRpcNaked(request, response);
    return response;
  }

  /** {@inheritDoc} */
  @Override
  public Nfs3RenameResponse wrapped_sendRename(NfsRenameRequest request) throws IOException {
    NfsResponseHandler<Nfs3RenameResponse> responseHandler =
        new NfsResponseHandler<Nfs3RenameResponse>() {

          /* (non-Javadoc)
           * @see com.emc.ecs.nfsclient.rpc.RpcResponseHandler#makeNewResponse()
           */
          @Override
          protected Nfs3RenameResponse makeNewResponse() {
            return new Nfs3RenameResponse();
          }
        };
    _rpcWrapper.callRpcWrapped(request, responseHandler);
    return responseHandler.getResponse();
  }

  /** {@inheritDoc} */
  @Override
  public Nfs3LinkRequest makeLinkRequest(
      byte[] fileHandle, byte[] parentDirectoryFileHandle, String name)
      throws FileNotFoundException {
    return new Nfs3LinkRequest(fileHandle, parentDirectoryFileHandle, name, _credential);
  }

  /** {@inheritDoc} */
  @Override
  public Nfs3LinkResponse sendLink(NfsLinkRequest request) throws IOException {
    Nfs3LinkResponse response = new Nfs3LinkResponse();
    _rpcWrapper.callRpcNaked(request, response);
    return response;
  }

  /** {@inheritDoc} */
  @Override
  public Nfs3LinkResponse wrapped_sendLink(NfsLinkRequest request) throws IOException {
    NfsResponseHandler<Nfs3LinkResponse> responseHandler =
        new NfsResponseHandler<Nfs3LinkResponse>() {

          /* (non-Javadoc)
           * @see com.emc.ecs.nfsclient.rpc.RpcResponseHandler#makeNewResponse()
           */
          @Override
          protected Nfs3LinkResponse makeNewResponse() {
            return new Nfs3LinkResponse();
          }
        };
    _rpcWrapper.callRpcWrapped(request, responseHandler);
    return responseHandler.getResponse();
  }

  /** {@inheritDoc} */
  @Override
  public NfsReaddirRequest makeReaddirRequest(
      byte[] directoryFileHandle, long cookie, long cookieverf, int count)
      throws FileNotFoundException {
    return new Nfs3ReaddirRequest(directoryFileHandle, cookie, cookieverf, count, _credential);
  }

  /** {@inheritDoc} */
  @Override
  public Nfs3ReaddirResponse getReaddir(NfsReaddirRequest request) throws IOException {
    Nfs3ReaddirResponse response = new Nfs3ReaddirResponse();
    _rpcWrapper.callRpcNaked(request, response);
    return response;
  }

  /** {@inheritDoc} */
  @Override
  public Nfs3ReaddirResponse wrapped_getReaddir(NfsReaddirRequest request) throws IOException {
    NfsResponseHandler<Nfs3ReaddirResponse> responseHandler =
        new NfsResponseHandler<Nfs3ReaddirResponse>() {

          /* (non-Javadoc)
           * @see com.emc.ecs.nfsclient.rpc.RpcResponseHandler#makeNewResponse()
           */
          @Override
          protected Nfs3ReaddirResponse makeNewResponse() {
            return new Nfs3ReaddirResponse();
          }
        };
    _rpcWrapper.callRpcWrapped(request, responseHandler);
    return responseHandler.getResponse();
  }

  /** {@inheritDoc} */
  @Override
  public Nfs3ReaddirResponse wrapped_getReaddir(
      NfsReaddirRequest request, final List<NfsDirectoryEntry> entries) throws IOException {
    NfsResponseHandler<Nfs3ReaddirResponse> responseHandler =
        new NfsResponseHandler<Nfs3ReaddirResponse>() {

          /* (non-Javadoc)
           * @see com.emc.ecs.nfsclient.rpc.RpcResponseHandler#makeNewResponse()
           */
          @Override
          protected Nfs3ReaddirResponse makeNewResponse() {
            return new Nfs3ReaddirResponse(entries);
          }
        };
    _rpcWrapper.callRpcWrapped(request, responseHandler);
    return responseHandler.getResponse();
  }

  /** {@inheritDoc} */
  @Override
  public NfsReaddirplusRequest makeReaddirplusRequest(
      byte[] directoryFileHandle, long cookie, long cookieverf, int dircount, int maxcount)
      throws FileNotFoundException {
    return new Nfs3ReaddirplusRequest(
        directoryFileHandle, cookie, cookieverf, dircount, maxcount, _credential);
  }

  /** {@inheritDoc} */
  @Override
  public Nfs3ReaddirplusResponse getReaddirplus(NfsReaddirplusRequest request) throws IOException {
    Nfs3ReaddirplusResponse response = new Nfs3ReaddirplusResponse();
    _rpcWrapper.callRpcNaked(request, response);
    return response;
  }

  /** {@inheritDoc} */
  @Override
  public Nfs3ReaddirplusResponse wrapped_getReaddirplus(NfsReaddirplusRequest request)
      throws IOException {
    NfsResponseHandler<Nfs3ReaddirplusResponse> responseHandler =
        new NfsResponseHandler<Nfs3ReaddirplusResponse>() {

          /* (non-Javadoc)
           * @see com.emc.ecs.nfsclient.rpc.RpcResponseHandler#makeNewResponse()
           */
          @Override
          protected Nfs3ReaddirplusResponse makeNewResponse() {
            return new Nfs3ReaddirplusResponse();
          }
        };
    _rpcWrapper.callRpcWrapped(request, responseHandler);
    return responseHandler.getResponse();
  }

  /** {@inheritDoc} */
  @Override
  public Nfs3ReaddirplusResponse wrapped_getReaddirplus(
      NfsReaddirplusRequest request, final List<NfsDirectoryPlusEntry> entries) throws IOException {
    NfsResponseHandler<Nfs3ReaddirplusResponse> responseHandler =
        new NfsResponseHandler<Nfs3ReaddirplusResponse>() {

          /* (non-Javadoc)
           * @see com.emc.ecs.nfsclient.rpc.RpcResponseHandler#makeNewResponse()
           */
          @Override
          protected Nfs3ReaddirplusResponse makeNewResponse() {
            return new Nfs3ReaddirplusResponse(entries);
          }
        };
    _rpcWrapper.callRpcWrapped(request, responseHandler);
    return responseHandler.getResponse();
  }

  /** {@inheritDoc} */
  @Override
  public NfsFsStat getNfsFsStat() throws IOException {
    return wrapped_getFsStat(makeFsStatRequest()).getFsStat();
  }

  /** {@inheritDoc} */
  @Override
  public Nfs3FsStatRequest makeFsStatRequest(byte[] fileHandle) throws FileNotFoundException {
    return new Nfs3FsStatRequest(fileHandle, _credential);
  }

  /** {@inheritDoc} */
  @Override
  public Nfs3FsStatRequest makeFsStatRequest() throws FileNotFoundException {
    return makeFsStatRequest(getRootFileHandle());
  }

  /** {@inheritDoc} */
  @Override
  public Nfs3FsStatResponse getFsStat(NfsFsStatRequest request) throws IOException {
    Nfs3FsStatResponse response = new Nfs3FsStatResponse();
    _rpcWrapper.callRpcNaked(request, response);
    return response;
  }

  /** {@inheritDoc} */
  @Override
  public Nfs3FsStatResponse wrapped_getFsStat(NfsFsStatRequest request) throws IOException {
    NfsResponseHandler<Nfs3FsStatResponse> responseHandler =
        new NfsResponseHandler<Nfs3FsStatResponse>() {

          /* (non-Javadoc)
           * @see com.emc.ecs.nfsclient.rpc.RpcResponseHandler#makeNewResponse()
           */
          @Override
          protected Nfs3FsStatResponse makeNewResponse() {
            return new Nfs3FsStatResponse();
          }
        };
    _rpcWrapper.callRpcWrapped(request, responseHandler);
    return responseHandler.getResponse();
  }

  /** {@inheritDoc} */
  @Override
  public NfsFsInfo getNfsFsInfo() throws IOException {
    return wrapped_getFsInfo(makeFsInfoRequest()).getFsInfo();
  }

  /** {@inheritDoc} */
  @Override
  public Nfs3FsInfoRequest makeFsInfoRequest(byte[] fileHandle) throws FileNotFoundException {
    return new Nfs3FsInfoRequest(fileHandle, _credential);
  }

  /** {@inheritDoc} */
  @Override
  public Nfs3FsInfoRequest makeFsInfoRequest() throws FileNotFoundException {
    return makeFsInfoRequest(getRootFileHandle());
  }

  /** {@inheritDoc} */
  @Override
  public Nfs3FsInfoResponse getFsInfo(NfsFsInfoRequest request) throws IOException {
    Nfs3FsInfoResponse response = new Nfs3FsInfoResponse();
    _rpcWrapper.callRpcNaked(request, response);
    return response;
  }

  /** {@inheritDoc} */
  @Override
  public Nfs3FsInfoResponse wrapped_getFsInfo(NfsFsInfoRequest request) throws IOException {
    NfsResponseHandler<Nfs3FsInfoResponse> responseHandler =
        new NfsResponseHandler<Nfs3FsInfoResponse>() {

          /* (non-Javadoc)
           * @see com.emc.ecs.nfsclient.rpc.RpcResponseHandler#makeNewResponse()
           */
          @Override
          protected Nfs3FsInfoResponse makeNewResponse() {
            return new Nfs3FsInfoResponse();
          }
        };
    _rpcWrapper.callRpcWrapped(request, responseHandler);
    return responseHandler.getResponse();
  }

  /** {@inheritDoc} */
  @Override
  public Nfs3PathconfRequest makePathconfRequest(byte[] fileHandle) throws FileNotFoundException {
    return new Nfs3PathconfRequest(fileHandle, _credential);
  }

  /** {@inheritDoc} */
  @Override
  public Nfs3PathconfResponse getPathconf(NfsPathconfRequest request) throws IOException {
    Nfs3PathconfResponse response = new Nfs3PathconfResponse();
    _rpcWrapper.callRpcNaked(request, response);
    return response;
  }

  /** {@inheritDoc} */
  @Override
  public Nfs3PathconfResponse wrapped_getPathconf(NfsPathconfRequest request) throws IOException {
    NfsResponseHandler<Nfs3PathconfResponse> responseHandler =
        new NfsResponseHandler<Nfs3PathconfResponse>() {

          /* (non-Javadoc)
           * @see com.emc.ecs.nfsclient.rpc.RpcResponseHandler#makeNewResponse()
           */
          @Override
          protected Nfs3PathconfResponse makeNewResponse() {
            return new Nfs3PathconfResponse();
          }
        };
    _rpcWrapper.callRpcWrapped(request, responseHandler);
    return responseHandler.getResponse();
  }

  /** {@inheritDoc} */
  @Override
  public Nfs3CommitRequest makeCommitRequest(
      byte[] fileHandle, long offsetToCommit, int dataSizeToCommit) throws FileNotFoundException {
    return new Nfs3CommitRequest(fileHandle, offsetToCommit, dataSizeToCommit, _credential);
  }

  /** {@inheritDoc} */
  @Override
  public Nfs3CommitResponse sendCommit(NfsCommitRequest request) throws IOException {
    Nfs3CommitResponse response = new Nfs3CommitResponse();
    _rpcWrapper.callRpcNaked(request, response, _server);
    return response;
  }

  /** {@inheritDoc} */
  @Override
  public Nfs3CommitResponse wrapped_sendCommit(NfsCommitRequest request) throws IOException {
    NfsResponseHandler<Nfs3CommitResponse> responseHandler =
        new NfsResponseHandler<Nfs3CommitResponse>() {

          /* (non-Javadoc)
           * @see com.emc.ecs.nfsclient.rpc.RpcResponseHandler#makeNewResponse()
           */
          @Override
          protected Nfs3CommitResponse makeNewResponse() {
            return new Nfs3CommitResponse();
          }
        };
    _rpcWrapper.callRpcWrapped(request, responseHandler, _server);
    return responseHandler.getResponse();
  }

  // TODO: Figure out what to do with the rest of the code in this class (following this comment).

  //  /**
  //   * @param path
  //   * @param fileHandle
  //   * @param offset
  //   * @param dataBuffer
  //   * @param isSync
  //   * @param serverIp
  //   * @param verifier
  //   * @return
  //   * @throws IOException
  //   */
  //  private AsyncWriteResult _write(String path, byte[] fileHandle, long offset, List<ByteBuffer>
  // dataBuffer,
  //          boolean isSync, String serverIp, Long verifier) throws IOException {
  //      return new AsyncWriteResult(response.getCount(), ip, response.getVerf());
  //  }

  /**
   * Read data from a file handle
   *
   * @param path the path of the file
   * @param fileHandle file handle of the file
   * @param offset offset of the file to read
   * @param length the length of the data buffer
   * @param data the data to be returned
   * @param eof is at the end-of-file
   * @return The number of bytes of data returned by the read. the number may be smaller than len
   */
  public int read(
      String path,
      byte[] fileHandle,
      long offset,
      int length,
      final byte[] data,
      final int pos,
      final MutableBoolean eof)
      throws IOException {
    Nfs3ReadRequest request = new Nfs3ReadRequest(fileHandle, offset, length, _credential);
    NfsResponseHandler<Nfs3ReadResponse> responseHandler =
        new NfsResponseHandler<Nfs3ReadResponse>() {

          /* (non-Javadoc)
           * @see com.emc.ecs.nfsclient.rpc.RpcResponseHandler#makeNewResponse()
           */
          @Override
          protected Nfs3ReadResponse makeNewResponse() {
            return new Nfs3ReadResponse(data, pos);
          }

          /* (non-Javadoc)
           * @see com.emc.ecs.nfsclient.nfs.NfsResponseHandler#checkResponse(com.emc.ecs.nfsclient.rpc.RpcRequest)
           */
          @Override
          public void checkResponse(RpcRequest request) throws IOException {
            super.checkResponse(request);
            eof.setValue(getResponse().isEof());
          }
        };
    _rpcWrapper.callRpcWrapped(request, responseHandler);
    return responseHandler.getResponse().getBytesRead();
  }

  public void fsync(String path, byte[] fh, final Long verifier) throws IOException {
    Nfs3CommitRequest request =
        new Nfs3CommitRequest(
            fh, 0 /* from beginning of file */, 0 /* till the end of it */, _credential);

    NfsResponseHandler<Nfs3CommitResponse> responseHandler =
        new NfsResponseHandler<Nfs3CommitResponse>() {

          /* (non-Javadoc)
           * @see com.emc.ecs.nfsclient.rpc.RpcResponseHandler#makeNewResponse()
           */
          @Override
          protected Nfs3CommitResponse makeNewResponse() {
            return new Nfs3CommitResponse();
          }

          /* (non-Javadoc)
           * @see com.emc.ecs.nfsclient.nfs.NfsResponseHandler#checkResponse(com.emc.ecs.nfsclient.rpc.RpcRequest)
           */
          @Override
          public void checkResponse(RpcRequest request) throws IOException {
            super.checkResponse(request);
            if (verifier != null && getResponse().getVerf() != verifier) {
              throw new NfsException(
                  NfsStatus.NFS3ERR_SERVERFAULT,
                  "COMMIT verifier changed (possible server restart): server="
                      + _server
                      + ", port="
                      + _port
                      + ", path="
                      + path
                      + ", expectedVerifier="
                      + verifier
                      + ", actualVerifier="
                      + getResponse().getVerf()
                      + ", request="
                      + request);
            }
          }
        };
    _rpcWrapper.callRpcWrapped(request, responseHandler, _server);
  }
}
