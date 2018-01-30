/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.storage;

import sirius.kernel.async.CompletionHandler;
import sirius.kernel.async.Promise;
import sirius.kernel.health.Exceptions;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;

/**
 * Buffers all contents on disk and uploads them in the storage once the stream is closed.
 */
class UpdatingOutputStream extends OutputStream {

    public static final byte[] EMPTY_BUFFER = new byte[0];
    private Storage storage;
    private StoredObject destination;
    private File bufferFile;
    private FileOutputStream buffer;
    private Promise<StoredObject> updateFilePromise = new Promise<>();

    protected UpdatingOutputStream(Storage storage, StoredObject obj) {
        this.storage = storage;
        this.destination = obj;
    }

    protected UpdatingOutputStream(Storage storage,
                                   StoredObject obj,
                                   CompletionHandler<StoredObject> completionHandler) {
        this(storage, obj);
        this.updateFilePromise.onComplete(completionHandler);
    }

    @Override
    public void write(int b) throws IOException {
        getBuffer().write(b);
    }

    @Override
    public void write(byte[] b) throws IOException {
        getBuffer().write(b);
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        getBuffer().write(b, off, len);
    }

    @Override
    public void flush() throws IOException {
        getBuffer().flush();
    }

    @Override
    public void close() throws IOException {
        try (InputStream emptyBufferInputStream = new ByteArrayInputStream(EMPTY_BUFFER)) {
            // Some implementations, e.g. Apache FTP server love to call close() several times, so we make it
            // idempotent by setting destination to null once it was closed the first time...
            if (destination == null) {
                return;
            }

            // If there is a bufferFile, we use its contents, otherwise no write operations at all took place,
            // therefore we clear the file by writing an empty byte array.
            if (bufferFile != null) {
                buffer.close();
                storage.updateFile(destination, bufferFile, null);
            } else {
                storage.updateFile(destination, emptyBufferInputStream, null, null, 0L);
            }
            updateFilePromise.success(destination);
        } catch (Exception e) {
            updateFilePromise.fail(e);
            throw e;
        } finally {
            if (bufferFile != null && bufferFile.exists()) {
                try {
                    Files.delete(bufferFile.toPath());
                } catch (Exception e) {
                    Exceptions.handle()
                              .to(Storage.LOG)
                              .error(e)
                              .withSystemErrorMessage("Cannot delete temporary file: %s", bufferFile.getAbsolutePath())
                              .handle();
                }
            }
            bufferFile = null;
            buffer = null;
            destination = null;
        }
    }

    protected FileOutputStream getBuffer() throws IOException {
        if (bufferFile == null) {
            if (destination == null) {
                throw new IOException("The stream has already been closed.");
            }
            bufferFile = File.createTempFile("storage", "upload");
            buffer = new FileOutputStream(bufferFile);
        }

        return buffer;
    }
}
