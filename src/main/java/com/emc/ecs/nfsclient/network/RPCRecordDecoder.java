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

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import io.netty.handler.codec.TooLongFrameException;
import java.util.List;

/** Delimits complete RPC records, retaining record markers for RecordMarkingUtil. */
public class RPCRecordDecoder extends ByteToMessageDecoder {
    private int scannedBytes;

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf buffer, List<Object> out) {
        // Do not consume a fragment until its entire record is available. Netty may
        // compact the cumulative buffer between calls, so retain a relative offset.
        while (buffer.readableBytes() - scannedBytes >= 4) {
            long marker = buffer.getUnsignedInt(buffer.readerIndex() + scannedBytes);
            long end = (long) scannedBytes + 4 + RecordMarkingUtil.maskFragmentSize(marker);
            if (end > Integer.MAX_VALUE) {
                throw new TooLongFrameException("RPC record exceeds maximum buffer size: remote="
                        + ctx.channel().remoteAddress() + ", recordBytes=" + end + ", limit=" + Integer.MAX_VALUE);
            }
            if (buffer.readableBytes() < end) {
                return;
            }
            scannedBytes = (int) end;
            if (RecordMarkingUtil.isLastFragment(marker)) {
                byte[] record = new byte[scannedBytes];
                buffer.readBytes(record);
                scannedBytes = 0;
                out.add(record);
                return;
            }
        }
    }
}
