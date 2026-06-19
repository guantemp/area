/*
 * Copyright (c) 2026. www.hoprxi.com All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package com.hoprxi.infrastructure;


import io.netty.buffer.ByteBuf;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.util.ReferenceCountUtil;
import reactor.core.publisher.Sinks;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/***
 * @author <a href="www.hoprxi.com/authors/guan xiangHuan">guan xiangHuang</a>
 * @since JDK21
 * @version 0.0.2 builder 2026/1/31
 */

public final class JsonByteBufOutputStream extends OutputStream {
    private final Sinks.Many<ByteBuf> sink;
    private final AtomicBoolean isCancelled;
    private ByteBuf buffer;
    private final int chunkSize;
    private volatile boolean closed = false;

    public JsonByteBufOutputStream(Sinks.Many<ByteBuf> sink, AtomicBoolean isCancelled, int chunkSize) {
        this.sink = Objects.requireNonNull(sink, "sink required");
        this.isCancelled = Objects.requireNonNull(isCancelled, "isCancelled required");
        this.chunkSize = chunkSize;
        this.buffer = PooledByteBufAllocator.DEFAULT.directBuffer(chunkSize);
    }

    public JsonByteBufOutputStream(Sinks.Many<ByteBuf> sink, AtomicBoolean isCancelled) {
        this(sink, isCancelled, 16 * 1024);//16kb
    }

    @Override
    public void write(int b) throws IOException {
        ensureWritable();
        if (buffer == null) buffer = PooledByteBufAllocator.DEFAULT.directBuffer(chunkSize);
        if (!buffer.isWritable()) {
            // 满了就发
            if (buffer != null && buffer.readableBytes() > 0) {
                // 发送当前缓冲区（所有权转移给下游）
                Sinks.EmitResult result = sink.tryEmitNext(buffer);
                if (result.isFailure() && result != Sinks.EmitResult.FAIL_TERMINATED) {
                    ReferenceCountUtil.safeRelease(buffer);
                    cancelAndEmitError(new IOException("Backpressure overflow, stream cancelled"));
                }
                buffer = null;  // 生产者不再持有
            }
            if (buffer == null) buffer = PooledByteBufAllocator.DEFAULT.directBuffer(chunkSize);
        }
        buffer.writeByte(b);
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        ensureWritable();
        if (len == 0) return;
        if (buffer == null) buffer = PooledByteBufAllocator.DEFAULT.directBuffer(chunkSize);

        int remaining = len;
        int offset = off;
        while (remaining > 0) {
            // 如果当前容量不足，先发送再分配新缓冲区
            if (buffer.writableBytes() == 0) {
                // 满了就发
                if (buffer != null && buffer.readableBytes() > 0) {
                    // 发送当前缓冲区（所有权转移给下游）
                    Sinks.EmitResult result = sink.tryEmitNext(buffer);
                    if (result.isFailure() && result != Sinks.EmitResult.FAIL_TERMINATED) {
                        ReferenceCountUtil.safeRelease(buffer);
                        cancelAndEmitError(new IOException("Backpressure overflow, stream cancelled"));
                    }
                    buffer = null;  // 生产者不再持有
                }
                if (buffer == null) buffer = PooledByteBufAllocator.DEFAULT.directBuffer(chunkSize);
            }
            int toWrite = Math.min(buffer.writableBytes(), remaining);
            buffer.writeBytes(b, offset, toWrite);
            offset += toWrite;
            remaining -= toWrite;
        }
    }

    @Override
    public void flush() throws IOException {
        if (isCancelled.get()) throw new IOException("Stream cancelled");
        if (buffer != null && buffer.readableBytes() > 0) {
            Sinks.EmitResult result = sink.tryEmitNext(buffer);
            if (result.isFailure()) {
                ReferenceCountUtil.safeRelease(buffer);
                cancelAndEmitError(new IOException("Backpressure overflow, stream cancelled"));
            }
            buffer = null;
        }
/*
        if (buffer.readableBytes() > 0) {
            ByteBuf slice = buffer.readRetainedSlice(buffer.readableBytes());
            //System.out.println("[" + System.currentTimeMillis() + "] >>> Flushing ByteBuf of size: " + slice.readableBytes());
            Sinks.EmitResult result = sink.tryEmitNext(slice);
            if (result.isFailure()) {
                slice.release();
                cancelAndEmitError(new IOException("Backpressure overflow, stream cancelled"));
            }
        }
        buffer.clear();//非常重要，没有这个在这里卡了1天
 */
    }

    @Override
    public void close() throws IOException {
        if (closed) return;
        try {
            // 发送最后一块（如果有数据）
            flush();
        } finally {
            closed = true;
            // 释放当前缓冲区（如果没有发送且还有数据，flush 已经发送则 buffer=null；如果没数据则直接释放）
            if (buffer != null) {
                ReferenceCountUtil.release(buffer);
                buffer = null;
            }
        }
    }

    private void cancelAndEmitError(IOException error) {
        isCancelled.set(true);
        sink.tryEmitError(error);
    }

    private void ensureWritable() throws IOException {
        if (isCancelled.get()) throw new IOException("Stream cancelled");
        if (closed) throw new IOException("Stream closed");
    }
}
