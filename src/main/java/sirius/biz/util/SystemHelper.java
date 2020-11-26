/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.util;

import sirius.kernel.di.std.Register;
import sirius.kernel.health.Exceptions;
import sirius.kernel.health.Log;
import sirius.web.security.HelperFactory;
import sirius.web.security.ScopeInfo;

import javax.annotation.Nonnull;

/**
 * Provides static System functionality and system environment detection as helper for in-template usage.
 */
public class SystemHelper {

    private static final String SYSTEM_HELPER = "SystemHelper";

    /**
     * This environment variable is defined in the docker-compose.yaml.erb template file and will alway be "prod",
     * "staging" or "test", as our node namings are fixes to that environment definitions.
     */
    private static final String ENVIRONMENT = "ENVIRONMENT";

    @Register
    public static class SystemHelperFactory implements HelperFactory<SystemHelper> {

        @Override
        public Class<SystemHelper> getHelperType() {
            return SystemHelper.class;
        }

        @Nonnull
        @Override
        public String getName() {
            return SYSTEM_HELPER;
        }

        @Override
        public SystemHelper make(ScopeInfo scopeInfo) {
            return new SystemHelper();
        }
    }

    /**
     * Returns the value of the given environment variable.
     *
     * @param varName the environment variable to look up
     * @return the value of the environment variable
     */
    public String getEnvironmentVariable(String varName) {
        return System.getenv(varName);
    }

    /**
     * Returns the current {@link SystemEnvironment} of the system.
     *
     * @return the current {@link SystemEnvironment} of the system.
     */
    public SystemEnvironment getCurrentSystemEnvironment() {
        try {
            return SystemEnvironment.valueOf(System.getenv(ENVIRONMENT).toUpperCase());
        } catch (IllegalArgumentException e) {
            Exceptions.handle()
                      .to(Log.SYSTEM)
                      .error(e)
                      .withSystemErrorMessage("The current system environment could not be "
                                              + "determined! '%s' is not a valid system environment! Setting system "
                                              + "environment to 'UNDEFINED'.", System.getenv(ENVIRONMENT).toUpperCase())
                      .handle();
            return SystemEnvironment.UNDEFINED;
        }
    }

    /**
     * Determines if the current System is a productive system.
     *
     * @return <tt>true</tt> if this is a productive system
     */
    public boolean isProductiveSystem() {
        return getCurrentSystemEnvironment() == SystemEnvironment.PROD;
    }

    /**
     * Defines the systems current environment
     */
    public enum SystemEnvironment {

        /**
         * Defines the productive system environment
         */
        PROD,

        /**
         * Defines the staging system environment
         */
        STAGING,

        /**
         * Defines the testing system environment
         */
        TEST,

        /**
         * Defines a not defined system environment. This can be used to indicate that a faulty environment variable
         * has been set. If a system environment is undefined, it is strongly recommended to check the environment
         * variables.
         */
        UNDEFINED
    }
}
