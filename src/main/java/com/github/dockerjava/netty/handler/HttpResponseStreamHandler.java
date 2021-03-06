package com.github.dockerjava.netty.handler;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;

import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.LinkedTransferQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import com.github.dockerjava.api.async.ResultCallback;

/**
 * Handler that converts an incoming byte stream to an {@link InputStream}.
 *
 * @author marcus
 */
public class HttpResponseStreamHandler extends SimpleChannelInboundHandler<ByteBuf> {

    private HttpResponseInputStream stream = new HttpResponseInputStream();

    public HttpResponseStreamHandler(ResultCallback<InputStream> resultCallback) {
        resultCallback.onNext(stream);
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, ByteBuf msg) throws Exception {
        stream.write(msg.copy());
    }

    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
        stream.close();
        super.channelReadComplete(ctx);
    }

    public static class HttpResponseInputStream extends InputStream {

        private AtomicBoolean closed = new AtomicBoolean(false);

        private LinkedTransferQueue<ByteBuf> queue = new LinkedTransferQueue<ByteBuf>();

        private ByteBuf current = null;

        public void write(ByteBuf byteBuf) {
            queue.put(byteBuf);
        }

        @Override
        public void close() throws IOException {
            closed.set(true);
            super.close();
        }

        @Override
        public int available() throws IOException {
            poll();
            return readableBytes();
        }

        private int readableBytes() {
            if (current != null) {
                return current.readableBytes();
            } else {
                return 0;
            }

        }

        @Override
        public int read() throws IOException {

            poll();

            if (readableBytes() == 0) {
                if (closed.get()) {
                    return -1;
                }
            }

            if (current != null && current.readableBytes() > 0) {
                return current.readByte() & 0xff;
            } else {
                return read();
            }
        }

        private void poll() {
            if (readableBytes() == 0) {
                try {
                    current = queue.poll(50, TimeUnit.MILLISECONDS);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
        }
    }
}
