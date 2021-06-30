/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.jobs.batch.file;

import sirius.biz.process.ProcessContext;
import sirius.kernel.di.std.Part;
import sirius.kernel.health.Exceptions;
import sirius.kernel.xml.XMLStructuredOutput;
import sirius.web.resources.Resource;
import sirius.web.resources.Resources;

import javax.annotation.Nonnull;
import javax.xml.transform.Source;
import javax.xml.transform.stream.StreamSource;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Provides basic functionalities to write data into XML file entries inside an archive.
 */
public abstract class XMLExportJob extends ArchiveExportJob {

    @Part
    private static Resources resources;

    private final boolean requireValidFile;
    private final String xsdResourcePath;
    protected XMLStructuredOutput xml;
    private OutputStream xmlOutputStream;

    /**
     * Creates a new job for the given process context.
     *
     * @param process the context in which the process will be executed
     */
    protected XMLExportJob(@Nonnull XMLExportJobFactory factory, ProcessContext process) {
        super(process);
        requireValidFile = process.getParameter(XMLExportJobFactory.VALID_FILE_PARAMETER).orElse(false);
        xsdResourcePath = factory.getXsdResourcePath();
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
        if (requireValidFile) {
            try {
                digestExportedFile((fileName, inputStream) -> validate(inputStream));
            } catch (Exception e) {
                process.handle(e);
            }
        }
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

    /**
     * Determines if the export should continue or be aborted because the xml file has to be valid but isn't.
     *
     * @param xmlInputStream the {@link InputStream} of an xml file which should be validated
     */
    protected void validate(InputStream xmlInputStream) {
        try {
            Source xmlSource = new StreamSource(xmlInputStream);
            Source xsdSource = new StreamSource(getXsdResource().openStream());

            XMLValidator xmlValidator = new XMLValidator(process);
            xmlValidator.validate(xmlSource, xsdSource);
        } catch (Exception e) {
            process.handle(e);
        }
    }

    @Nonnull
    protected Resource getXsdResource() throws Exception {
        return resources.resolve(xsdResourcePath)
                        .orElseThrow(() -> Exceptions.createHandled()
                                                     .withSystemErrorMessage("Could not find XSD file '%s'",
                                                                             xsdResourcePath)
                                                     .handle());
    }
}
