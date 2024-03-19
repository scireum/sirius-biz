/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.storage.layer3.downlink.ssh;

import org.apache.sshd.common.io.IoInputStream;
import org.apache.sshd.common.io.IoOutputStream;
import org.apache.sshd.common.io.IoReadFuture;
import org.apache.sshd.common.util.buffer.ByteArrayBuffer;
import org.apache.sshd.server.Environment;
import org.apache.sshd.server.ExitCallback;
import org.apache.sshd.server.channel.ChannelSession;
import org.apache.sshd.server.command.AsyncCommand;
import sirius.kernel.health.Exceptions;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Provides NO_OP shell.
 * <p>
 * This is required by some SFTP clients, which first initiate SSH and then switch over to SFTP.
 */
class NoopShell implements AsyncCommand {

    private ExitCallback exitCallback;
    private IoInputStream inputStream;
    private IoOutputStream outputStream;

    private final ByteArrayBuffer readBuffer = new ByteArrayBuffer(1);

    NoopShell(ChannelSession channelSession) {
        // The given session isn't needed...
    }

    @Override
    public void setInputStream(InputStream in) {
        // ignored
    }

    @Override
    public void setOutputStream(OutputStream out) {
        // ignored
    }

    @Override
    public void setErrorStream(OutputStream err) {
        // ignored
    }

    @Override
    public void setExitCallback(ExitCallback callback) {
        this.exitCallback = callback;
    }

    @Override
    public void start(ChannelSession channelSession, Environment environment) throws IOException {
        inputStream.read(readBuffer).addListener(this::processInput);
    }

    @Override
    public void destroy(ChannelSession channelSession) throws Exception {
        // Ignored...
    }

    private void processInput(IoReadFuture future) {
        try {
            if (future.getRead() == 0) {
                exitCallback.onExit(-2);
                return;
            }

            // Properly handle CTRL+C...
            byte data = future.getBuffer().rawByte(0);
            if (data == 0x03) {
                exitCallback.onExit(0);
                return;
            }

            // Simply echo the input...
            outputStream.writeBuffer(future.getBuffer());
            readBuffer.clear();
            inputStream.read(readBuffer).addListener(this::processInput);
        } catch (IOException exception) {
            Exceptions.ignore(exception);
            exitCallback.onExit(-1);
        }
    }

    @Override
    public void setIoInputStream(IoInputStream in) {
        this.inputStream = in;
    }

    @Override
    public void setIoOutputStream(IoOutputStream out) {
        this.outputStream = out;
    }

    @Override
    public void setIoErrorStream(IoOutputStream err) {
        // ignored
    }
}
