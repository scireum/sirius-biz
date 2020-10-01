/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.jobs.batch.file;

import sirius.biz.process.ProcessContext;
import sirius.biz.storage.layer3.FileOrDirectoryParameter;
import sirius.kernel.xml.XMLStructuredOutput;

import java.io.IOException;
import java.io.OutputStream;

/**
 * Provides basic functionalities to write data into XML file entries inside an archive.
 */
public abstract class XMLExportJob extends ArchiveExportJob {

    protected XMLStructuredOutput xml;
    private OutputStream xmlOutputStream;

    /**
     * Creates a new job for the given process context.
     *
     * @param destinationParameter the parameter used to select the destination for the file being written
     * @param process              the context in which the process will be executed
     */
    protected XMLExportJob(FileOrDirectoryParameter destinationParameter, ProcessContext process) {
        super(destinationParameter, process);
    }

    protected void initializeXmlFile(String filename) throws IOException {
        closeOpenStream();
        xmlOutputStream = createEntry(filename);
        xml = new XMLStructuredOutput(xmlOutputStream);
    }

    @Override
    public void close() throws IOException {
        closeOpenStream();
        super.close();
    }

    private void closeOpenStream() {
        if (xmlOutputStream != null) {
            try {
                xmlOutputStream.close();
            } catch (IOException e) {
                process.handle(e);
            }
        }
    }
}
