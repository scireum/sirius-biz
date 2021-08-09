/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.storage.layer3.downlink.ssh.sftp;

import sirius.biz.storage.layer3.VirtualFile;
import sirius.kernel.health.Exceptions;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Provides a simple implementation of the seekable channel.
 * <p>
 * This implementation doesn't actually support much seeking, as this is not supported by the
 * {@link sirius.biz.storage.layer3.VirtualFileSystem}. Nevertheless, as long as all writes are sequential and don't
 * skip any data, we fulfill all calls. Also we can skip some bytes when reading as long as it is in the forward
 * direction.
 */
class BridgeSeekableByteChannel implements SeekableByteChannel {

    private final VirtualFile virtualFile;
    private InputStream in;
    private OutputStream out;
    private final AtomicLong position = new AtomicLong(0);

    protected BridgeSeekableByteChannel(VirtualFile virtualFile) {
        this.virtualFile = virtualFile;
    }

    @Override
    public int read(ByteBuffer destination) throws IOException {
        if (in == null) {
            in = virtualFile.createInputStream();
        }

        int read = in.read(destination.array(),
                           destination.arrayOffset() + destination.position(),
                           destination.remaining());

        int lastRead = read;
        while (lastRead > 0 && read < destination.remaining()) {
            lastRead = in.read(destination.array(),
                               destination.arrayOffset() + destination.position() + read,
                               destination.remaining() - read);
            read += lastRead;
        }

        if (read > 0) {
            position.addAndGet(read);
        }

        return read;
    }

    @Override
    public int write(ByteBuffer src) throws IOException {
        if (out == null) {
            out = virtualFile.createOutputStream();
        }

        int remaining = src.remaining();
        out.write(src.array(), src.arrayOffset() + src.position(), remaining);
        position.addAndGet(remaining);

        return remaining;
    }

    @Override
    public long position() throws IOException {
        return position.get();
    }

    @Override
    public SeekableByteChannel position(long newPosition) throws IOException {
        long currentPosition = position.get();
        if (newPosition == currentPosition) {
            return this;
        }

        if (newPosition < currentPosition) {
            throw new IOException("Cannot skip to " + newPosition);
        }

        if (in != null) {
            currentPosition += in.skip(newPosition - currentPosition);
            position.set(currentPosition);
        } else if (out != null) {
            long remaining = newPosition - currentPosition;
            while (remaining > 0) {
                byte[] buffer = new byte[(int) Math.min(8192, remaining)];
                out.write(buffer, 0, buffer.length);
                remaining -= buffer.length;
                position.addAndGet(buffer.length);
            }
        }

        return this;
    }

    @Override
    public long size() throws IOException {
        return Math.max(position.get(), virtualFile.size());
    }

    @Override
    public SeekableByteChannel truncate(long size) throws IOException {
        throw new UnsupportedOperationException("truncate");
    }

    @Override
    public boolean isOpen() {
        return in != null || out != null;
    }

    @Override
    public void close() throws IOException {
        if (in != null) {
            try {
                in.close();
                in = null;
            } catch (IOException e) {
                Exceptions.ignore(e);
            }
        }
        if (out != null) {
            try {
                out.close();
                out = null;
            } catch (IOException e) {
                Exceptions.ignore(e);
            }
        }
    }
}
