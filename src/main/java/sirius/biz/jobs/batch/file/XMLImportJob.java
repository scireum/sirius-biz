/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.jobs.batch.file;

import sirius.biz.jobs.params.SelectStringParameter;
import sirius.biz.process.ProcessContext;
import sirius.biz.process.logs.ProcessLog;
import sirius.kernel.commons.Producer;
import sirius.kernel.commons.Strings;
import sirius.kernel.di.std.Part;
import sirius.kernel.health.Exceptions;
import sirius.kernel.xml.NodeHandler;
import sirius.kernel.xml.XMLReader;
import sirius.web.resources.Resource;
import sirius.web.resources.Resources;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.xml.transform.Source;
import javax.xml.transform.stream.StreamSource;
import java.io.InputStream;
import java.util.function.BiConsumer;

/**
 * Provides a base class for jobs which import XML files via a {@link XMLImportJobFactory}.
 */
public abstract class XMLImportJob extends FileImportJob {

    protected static final String XSD_SCHEMA_PARAMETER_NAME = "xsdSchema";

    @Part
    private static Resources resources;

    private final String validationXsdPath;
    private int passes = 1;

    /**
     * Creates a new job for the given factory and process.
     *
     * @param process the process context itself
     */
    protected XMLImportJob(ProcessContext process) {
        super(process);
        validationXsdPath = process.get(XSD_SCHEMA_PARAMETER_NAME).asString();
    }

    /**
     * Sets how many passes should be executed over the input xml file.
     * <p>
     * This is useful in some xml structures which needs different handles for each pass
     *
     * @param passes the amount of passes to perform. Default = 1
     * @return the job itself for fluent calls
     */
    public XMLImportJob withPasses(int passes) {
        this.passes = passes;
        return this;
    }

    protected static SelectStringParameter createSchemaParameter() {
        return new SelectStringParameter(XSD_SCHEMA_PARAMETER_NAME, "$XMLImportJobFactory.xsdSchema").withDescription(
                "$XMLImportJobFactory.xsdSchema.help");
    }

    @Override
    protected void executeForStream(String filename, Producer<InputStream> inputSupplier) throws Exception {
        if (isValid(inputSupplier)) {
            for (int currentPass = 1; currentPass <= passes; currentPass++) {
                try (InputStream inputStream = inputSupplier.create()) {
                    executeForValidStream(inputStream, currentPass);
                }
            }
        } else {
            process.log(ProcessLog.error()
                                  .withNLSKey("XMLImportJob.invalidXMLDetected")
                                  .withContext("fileName", filename));
        }
    }

    /**
     * Determines if the import should continue or be aborted because the xml file has to be valid but isn't
     *
     * @param inputSupplier the {@link InputStream} of an xml file which should be validated
     * @return <tt>true</tt> if the import should continue, <tt>false</tt> otherwise
     * @throws Exception in case of an exception during validation
     */
    protected boolean isValid(Producer<InputStream> inputSupplier) throws Exception {
        if (Strings.isEmpty(validationXsdPath)) {
            return true;
        }

        try (InputStream in = inputSupplier.create()) {
            Source xmlSource = new StreamSource(in);
            Source xsdSource = new StreamSource(getXsdResource().openStream());

            XMLValidator xmlValidator = new XMLValidator(process);

            return xmlValidator.validate(xmlSource, xsdSource);
        }
    }

    @Nonnull
    protected Resource getXsdResource() throws Exception {
        return resources.resolve(validationXsdPath)
                        .orElseThrow(() -> Exceptions.createHandled()
                                                     .withSystemErrorMessage("Could not find XSD file '%s'",
                                                                             validationXsdPath)
                                                     .handle());
    }

    protected void executeForValidStream(InputStream in, int currentPass) throws Exception {
        XMLReader reader = new XMLReader();
        registerHandlers(currentPass, reader::addHandler);
        reader.parse(in, this::resolveResource);
    }

    @Override
    protected boolean canHandleFileExtension(String fileExtension) {
        return "xml".equals(fileExtension);
    }

    /**
     * Registers handlers which are invoked for each appropriate node or sub tree parsed by the reader.
     *
     * @param pass    the current pass being made over the xml file
     * @param handler the handler to register
     */
    protected abstract void registerHandlers(int pass, BiConsumer<String, NodeHandler> handler);

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
