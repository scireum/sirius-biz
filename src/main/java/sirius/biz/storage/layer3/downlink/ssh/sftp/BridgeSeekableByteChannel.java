/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.storage.layer3.downlink.ssh.sftp;

import sirius.biz.storage.layer3.VirtualFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.util.concurrent.atomic.AtomicLong;

class BridgeSeekableByteChannel implements SeekableByteChannel {

    private VirtualFile virtualFile;
    private InputStream in;
    private OutputStream out;
    private AtomicLong position = new AtomicLong(0);

    protected BridgeSeekableByteChannel(VirtualFile virtualFile) {
        this.virtualFile = virtualFile;
    }

    @Override
    public int read(ByteBuffer dst) throws IOException {
        if (in == null) {
            in = virtualFile.createInputStream();
        }

//        int remaining = dst.remaining();
        int read = in.read(dst.array(), dst.arrayOffset() + dst.position(), dst.remaining());
        if (read > 0) {
            position.addAndGet(read);
        }

//        System.out.println(remaining + "/" + read);

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
        if (newPosition < position.get() || out != null) {
            throw new UnsupportedOperationException();
        }

        if (newPosition == position.get()) {
            return this;
        }

//        if ()

        return this;
    }

    @Override
    public long size() throws IOException {
        return virtualFile.size();
    }

    @Override
    public SeekableByteChannel truncate(long size) throws IOException {
        throw new UnsupportedOperationException();
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
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        if (out != null) {
            try {
                out.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
