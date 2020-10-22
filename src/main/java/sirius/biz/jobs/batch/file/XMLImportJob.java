/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.jobs.batch.file;

import sirius.biz.process.ProcessContext;
import sirius.biz.process.logs.ProcessLog;
import sirius.biz.storage.layer1.FileHandle;
import sirius.kernel.commons.Files;
import sirius.kernel.commons.Strings;
import sirius.kernel.di.std.Part;
import sirius.kernel.health.Exceptions;
import sirius.kernel.xml.XMLReader;
import sirius.web.resources.Resource;
import sirius.web.resources.Resources;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.xml.transform.Source;
import javax.xml.transform.stream.StreamSource;
import java.io.InputStream;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Provides a base class for jobs which import XML files via a {@link XMLImportJobFactory}.
 */
public abstract class XMLImportJob extends FileImportJob {

    @Part
    private static Resources resources;

    private final String validationXsdPath;

    /**
     * Creates a new job for the given factory and process.
     *
     * @param factory the factory of the surrounding import job
     * @param process the process context itself
     */
    protected XMLImportJob(XMLImportJobFactory factory, ProcessContext process) {
        super(factory.fileParameter, process);
        validationXsdPath = process.getParameter(factory.requireValidFile).orElse(null);
    }

    @Override
    protected void executeForSingleFile(String fileName, FileHandle fileHandle) throws Exception {
        if (Strings.isFilled(validationXsdPath)) {
            try (InputStream in = fileHandle.getInputStream()) {
                if (!validate(in)) {
                    process.log(ProcessLog.error()
                                          .withNLSKey("XMLImportJob.importCanceled")
                                          .withContext("fileName", fileName));
                    return;
                }
            }
        }
        try (InputStream in = fileHandle.getInputStream()) {
            executeForStream(fileName, in);
        }
    }

    @Override
    protected void executeForArchive(FileHandle fileHandle) throws Exception {
        process.log(ProcessLog.info().withNLSKey("FileImportJob.importingZipFile"));

        ZipFile zipFile = new ZipFile(fileHandle.getFile());
        int filesImported = 0;

        Enumeration<? extends ZipEntry> zipEntries = zipFile.entries();
        while (zipEntries.hasMoreElements()) {
            ZipEntry zipEntry = zipEntries.nextElement();

            if (!isHiddenFile(zipEntry.getName())
                && canHandleFileExtension(Files.getFileExtension(zipEntry.getName()))) {
                process.log(ProcessLog.info()
                                      .withNLSKey("FileImportJob.importingZippedFile")
                                      .withContext("filename", zipEntry.getName()));
                executeForArchivedFile(zipFile, zipEntry);
                filesImported++;
            }
        }

        if (filesImported == 0) {
            throw Exceptions.createHandled().withNLSKey("FileImportJob.noZippedFileFound").handle();
        }
    }

    protected void executeForArchivedFile(ZipFile zipFile, ZipEntry zipEntry) throws Exception {
        if (Strings.isFilled(validationXsdPath)) {
            try (InputStream in = zipFile.getInputStream(zipEntry)) {
                if (!validate(in)) {
                    process.log(ProcessLog.error()
                                          .withNLSKey("XMLImportJob.importCanceled")
                                          .withContext("fileName", zipEntry.getName()));
                    return;
                }
            }
        }
        try (InputStream in = zipFile.getInputStream(zipEntry)) {
            executeForStream(zipEntry.getName(), in);
        }
    }

    /**
     * Determines if the import should continue or be aborted because the xml file has to be valid but isn't
     *
     * @param xmlInputStream the {@link InputStream} of an xml file which should be validated
     * @return <tt>true</tt> if the import should continue, <tt>false</tt> otherwise
     * @throws Exception in case of an exception during validation
     */
    protected boolean validate(InputStream xmlInputStream) throws Exception {
        Source xmlSource = new StreamSource(xmlInputStream);
        Source xsdSource = new StreamSource(getXsdResource().openStream());

        XMLValidator xmlValidator = new XMLValidator(process);

        return xmlValidator.validate(xmlSource, xsdSource);
    }

    @Nonnull
    protected Resource getXsdResource() throws Exception {
        return resources.resolve(validationXsdPath)
                        .orElseThrow(() -> Exceptions.createHandled()
                                                     .withSystemErrorMessage("Could not find XSD file '%s'",
                                                                             validationXsdPath)
                                                     .handle());
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
