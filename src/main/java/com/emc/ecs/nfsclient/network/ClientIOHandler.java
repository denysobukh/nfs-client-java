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
package com.emc.ecs.nfsclient.network;

import com.emc.ecs.nfsclient.rpc.Xdr;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.net.InetSocketAddress;

/** Handles RPC responses and connection termination for one channel. */
public class ClientIOHandler extends SimpleChannelInboundHandler<byte[]> {
    private static final Logger LOG = LoggerFactory.getLogger(ClientIOHandler.class);
    private final Connection connection;

    public ClientIOHandler(Connection connection) {
        this.connection = connection;
    }

    public InetSocketAddress getRemoteAddress() {
        return connection.getRemoteAddress();
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        connection.close();
        super.channelInactive(ctx);
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, byte[] response) {
        Xdr xdr = RecordMarkingUtil.removeRecordMarking(response);
        connection.notifySender(xdr.getXid(), xdr);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        if (!(cause instanceof java.net.BindException)) {
            LOG.error("Exception on connection to " + getRemoteAddress(), cause);
            ctx.close();
        }
    }
}
