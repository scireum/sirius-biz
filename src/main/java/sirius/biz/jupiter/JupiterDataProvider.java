/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.jupiter;

import sirius.kernel.di.std.AutoRegister;

import java.io.OutputStream;

/**
 * Provides data to be stored in a file in the local Jupiter repository.
 * <p>
 * The {@link JupiterSync} will invoke all data providers each night to update their file contents in the local
 * repository. This will then be synchronized with the repository in the Jupiter instance itself so that
 * InfoGraphDB and other consumers can load this data.
 * <p>
 * Note that a data provider must be {@link sirius.kernel.di.std.Register registered} to be visible to the framework.
 */
@AutoRegister
public interface JupiterDataProvider {

    /**
     * Returns the name of this provider to logging purposes.
     *
     * @return the name of this provider
     */
    String getName();

    /**
     * Returns the filename of the file to be stored and transferred to Jupiter.
     *
     * @return the filename or path to be stored in Jupiter
     */
    String getFilename();

    /**
     * Executes the data provider so that all data is written into the given output stream.
     *
     * @param outputStream the destination for all data to be stored
     * @throws Exception in case of an error while generating data
     */
    void execute(OutputStream outputStream) throws Exception;
}
