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
package com.emc.ecs.nfsclient.portmap;

import com.emc.ecs.nfsclient.network.NetMgr;
import com.emc.ecs.nfsclient.rpc.RpcException;
import com.emc.ecs.nfsclient.rpc.RpcStatus;
import com.emc.ecs.nfsclient.rpc.Xdr;

import java.io.IOException;

import org.apache.commons.lang3.NotImplementedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Used to query the port used for a service (mount or NFS), as specified by
 * https://tools.ietf.org/html/rfc1833.
 * 
 * @author seibed
 */
public class Portmapper {

    private static final Logger LOG = LoggerFactory.getLogger(Portmapper.class);

    /**
     * TCP port for Portmap calls, as specified by
     * https://tools.ietf.org/html/rfc1833.
     */
    private static final int PMAP_PORT = 111;

    /**
     * Maximum request size in bytes.
     */
    private static final int PORTMAP_MAX_REQUEST_SIZE = 128;

    /**
     * RPC timeout in seconds.
     */
    private static int PORTMAP_RPC_TIMEOUT = 10;

    /**
     * Maximum number of retries. Calls are wrapped in case of temporary issues.
     */
    private static int _maxRetry = 2;

    /**
     * <ul>
     * <li>If <code>true</code>, use a privileged local port (below 1024) for
     * Portmap RPC communication.</li>
     * <li>If <code>false</code>, use any non-privileged local port for Portmap
     * RPC communication.</li>
     * </ul>
     */
    private static boolean _usePrivilegedPort = false;

    /**
     * Given program and version of a service, query its tcp port number
     * 
     * @param program
     *            The program number, used to identify it for RPC calls.
     * @param version
     *            The program version number, used to identify it for RPC calls.
     * @param serverIP
     *            The server IP address.
     * @return The port number for the program.
     */
    public static int queryPortFromPortMap(int program, int version, String serverIP) throws IOException {
        GetPortResponse response = null;
        GetPortRequest request = new GetPortRequest(program, version);
        for (int i = 0; i < _maxRetry; ++i) {
            try {
                LOG.debug("Querying portmapper: server={}, port={}, program={}, version={}, attempt={}/{}",
                        serverIP, PMAP_PORT, program, version, i + 1, _maxRetry);
                Xdr portmapXdr = new Xdr(PORTMAP_MAX_REQUEST_SIZE);
                request.marshalling(portmapXdr);

                Xdr reply = NetMgr.getInstance().sendAndWait(serverIP, PMAP_PORT, _usePrivilegedPort, portmapXdr,
                        PORTMAP_RPC_TIMEOUT);

                response = new GetPortResponse();
                response.unmarshalling(reply);
                break;
            } catch (RpcException e) {
                handleRpcException(e, i, serverIP, program, version);
            }
        }

        int port = response.getPort();
        if (port == 0) { // A port value of zeros means the program has not been
                         // registered.
            String msg = String.format("Portmapper has no TCP service registration: server=%s, port=%d, program=%d,"
                            + " version=%d. Check that the requested service/version is enabled and registered with rpcbind."
                            + " NFSv3 requires both NFS (100003) and MOUNT (100005) services.",
                    serverIP, PMAP_PORT, program, version);
            LOG.warn("{}", msg);
            throw new IOException(msg);
        }
        LOG.debug("Resolved RPC service port: server={}, program={}, version={}, servicePort={}",
                serverIP, program, version, port);
        return port;
    }

    /**
     * Decide whether to retry or throw exception.
     * 
     * @param e
     *            The exception.
     * @param attemptNumber
     *            The number of attempts so far.
     * @throws IOException
     */
    private static void handleRpcException(RpcException e, int attemptNumber, String server, int program, int version)
            throws IOException {
        boolean networkError = e.getStatus().equals(RpcStatus.NETWORK_ERROR);
        if (networkError && attemptNumber + 1 < _maxRetry) {
            LOG.warn("Portmapper query failed; retrying: server={}, port={}, program={}, version={}, attempt={}/{},"
                            + " status={}: {}", server, PMAP_PORT, program, version,
                    attemptNumber + 1, _maxRetry, e.getStatus(), e.getMessage(), e);
            return;
        }
        String message = String.format("Portmapper %s failure: server=%s, port=%d, program=%d, version=%d, attempt=%d/%d,"
                                + " status=%s: %s. Check rpcbind availability and TCP port 111 reachability.",
                        networkError ? "network" : "RPC", server, PMAP_PORT, program, version,
                        attemptNumber + 1, _maxRetry, e.getStatus(), e.getMessage());
        LOG.warn("{}", message, e);
        throw new IOException(message, e);
    }

    /**
     * Never called.
     */
    private Portmapper() {
        throw new NotImplementedException("No class instances should be needed.");
    }

}
