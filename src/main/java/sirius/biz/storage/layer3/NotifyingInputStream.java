/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.storage.layer3;

import sirius.kernel.async.Future;

import java.io.IOException;
import java.io.InputStream;

/**
 * Wraps a given {@link InputStream} and invokes a callback once the stream was fully read.
 */
public class NotifyingInputStream extends InputStream {

    private InputStream delegate;
    private Future closeFuture;

    /**
     * Creates a new wrapper for the given stream and callback.
     *
     * @param delegate    the stream to wrap
     * @param closeFuture the future which will be notified once processing has been finished
     */
    public NotifyingInputStream(InputStream delegate, Future closeFuture) {
        this.delegate = delegate;
        this.closeFuture = closeFuture;
    }

    @Override
    public void close() throws IOException {
        try {
            delegate.close();
        } catch (IOException e) {
            if (!closeFuture.isCompleted()) {
                closeFuture.fail(e);
            }
            throw e;
        }

        if (!closeFuture.isCompleted()) {
            closeFuture.success();
        }
    }

    @Override
    public int read() throws IOException {
        return delegate.read();
    }

    @Override
    public int read(byte[] b) throws IOException {
        return delegate.read(b);
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        return delegate.read(b, off, len);
    }

    @Override
    public long skip(long n) throws IOException {
        return delegate.skip(n);
    }

    @Override
    public int available() throws IOException {
        return delegate.available();
    }

    @Override
    public void mark(int readlimit) {
        delegate.mark(readlimit);
    }

    @Override
    public void reset() throws IOException {
        delegate.reset();
    }

    @Override
    public boolean markSupported() {
        return delegate.markSupported();
    }
}
