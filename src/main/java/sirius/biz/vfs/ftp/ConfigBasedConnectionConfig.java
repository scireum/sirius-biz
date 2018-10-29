/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.vfs.ftp;

import org.apache.ftpserver.ConnectionConfig;
import sirius.kernel.di.std.ConfigValue;

/**
 * Provides a FTP config based on the system configuration.
 */
class ConfigBasedConnectionConfig implements ConnectionConfig {

    @ConfigValue("vfs.ftp.maxLoginFailures")
    private static int maxLoginFailures;

    @ConfigValue("vfs.ftp.maxClients")
    private static int maxClients;

    @ConfigValue("vfs.ftp.maxThreads")
    private static int maxThreads;

    @Override
    public int getLoginFailureDelay() {
        return 1;
    }

    @Override
    public int getMaxAnonymousLogins() {
        return 0;
    }

    @Override
    public int getMaxLoginFailures() {
        return maxLoginFailures;
    }

    @Override
    public int getMaxLogins() {
        return maxClients;
    }

    @Override
    public boolean isAnonymousLoginEnabled() {
        return false;
    }

    @Override
    public int getMaxThreads() {
        return maxThreads;
    }
}
