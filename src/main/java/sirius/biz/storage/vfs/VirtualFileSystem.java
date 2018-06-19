/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.storage.vfs;

import sirius.biz.storage.vfs.ftp.FTPBridge;
import sirius.kernel.Startable;
import sirius.kernel.Stoppable;
import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.Priorized;
import sirius.kernel.di.std.Register;
import sirius.kernel.health.Log;

/**
 * Permits to provide a virtual file system for outside applications to access internal data.
 * <p>
 * Mainly, this will start an internal FTP server to make the file system accessible, but other connectors might be
 * added in the future.
 */
@Register
public class VirtualFileSystem implements Startable, Stoppable {

    public static final Log LOG = Log.get("vfs");

    @Part
    private FTPBridge ftpBridge;

    @Override
    public int getPriority() {
        return Priorized.DEFAULT_PRIORITY;
    }

    @Override
    public void started() {
        ftpBridge.createAndStartServer();
    }

    @Override
    public void stopped() {
        ftpBridge.stop();
    }
}
