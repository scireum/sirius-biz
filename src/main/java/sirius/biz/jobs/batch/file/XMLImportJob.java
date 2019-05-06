/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.jobs.batch.file;

import sirius.biz.process.ProcessContext;
import sirius.kernel.xml.XMLReader;

import javax.annotation.Nullable;
import java.io.InputStream;

/**
 * Provides a base class for jobs which import XML files via a {@link XMLImportJobFactory}.
 */
public abstract class XMLImportJob extends FileImportJob {

    /**
     * Creates a new job for the given factory and process.
     *
     * @param factory the factory of the surrounding import job
     * @param process the process context itself
     */
    protected XMLImportJob(XMLImportJobFactory factory, ProcessContext process) {
        super(factory.fileParameter, process);
    }

    @Override
    protected void executeForStream(String filename, InputStream in) throws Exception {
        XMLReader reader = new XMLReader();
        registerHandlers(reader);
        reader.parse(in, this::resolveResource);
    }

    @Override
    protected boolean canHandleFileExtension(String fileExtension) {
        return "xml".equals(fileExtension);
    }

    /**
     * Registers handlers which are invoked for each appropriate node or sub tree parsed by the reader.
     *
     * @param reader the reader to enhance with handlers
     */
    protected abstract void registerHandlers(XMLReader reader);

    /**
     * Responsible for resolving resources (DTD, schema) referenced in the XML file.
     *
     * @param name the resource to resolve
     * @return the contents of the resource as stream or <tt>null</tt> if the resource cannot be resolved
     */
    @Nullable
    protected InputStream resolveResource(String name) {
        return null;
    }
}
