/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.jobs.batch.file;

import sirius.biz.importer.AfterNodeLoadEvent;
import sirius.biz.jobs.params.Parameter;
import sirius.biz.jobs.params.SelectStringParameter;
import sirius.biz.process.ProcessContext;
import sirius.biz.process.logs.ProcessLog;
import sirius.kernel.commons.Monoflop;
import sirius.kernel.commons.Producer;
import sirius.kernel.commons.Strings;
import sirius.kernel.di.std.Part;
import sirius.kernel.health.Exceptions;
import sirius.kernel.xml.NoContentNodeHandler;
import sirius.kernel.xml.NodeHandler;
import sirius.kernel.xml.StructuredNode;
import sirius.kernel.xml.XMLReader;
import sirius.web.resources.Resource;
import sirius.web.resources.Resources;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.xml.transform.Source;
import javax.xml.transform.stream.StreamSource;
import java.io.InputStream;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Provides a base class for jobs which import XML files via a {@link XMLImportJobFactory}.
 */
public abstract class XMLImportJob extends FileImportJob {

    protected static final Parameter<String> XSD_SCHEMA_PARAMETER = createSchemaParameter(null);

    @Part
    private static Resources resources;

    private final String validationXsdPath;

    /**
     * Creates a new job for the given process context.
     *
     * @param process the context in which the process will be executed
     */
    protected XMLImportJob(ProcessContext process) {
        super(process);
        validationXsdPath = process.getParameter(XSD_SCHEMA_PARAMETER).orElse(null);
    }

    protected static Parameter<String> createSchemaParameter(Map<String, String> paths) {
        SelectStringParameter parameter =
                new SelectStringParameter("xsdSchema", "$XMLImportJobFactory.xsdSchema").withDescription(
                        "$XMLImportJobFactory.xsdSchema.help");
        if (paths != null) {
            paths.forEach(parameter::withEntry);
        }
        return parameter.build();
    }

    @Override
    protected void executeForStream(String filename, Producer<InputStream> inputSupplier) throws Exception {
        if (!isValid(inputSupplier)) {
            process.log(ProcessLog.error()
                                  .withNLSKey("XMLImportJob.invalidXMLDetected")
                                  .withContext("fileName", filename));
            return;
        }

        Monoflop firstStage = Monoflop.create();
        for (Consumer<BiConsumer<String, NodeHandler>> handlerConsumer : fetchStages()) {
            if (firstStage.successiveCall()) {
                // If an import is split into several stages, we almost always want to await the completion of
                // any running side-task before starting the next stage....
                process.awaitSideTaskCompletion();
            }

            try (InputStream inputStream = inputSupplier.create()) {
                executeProcessingStage(inputStream, handlerConsumer);
            } catch (Exception exception) {
                process.log(ProcessLog.error().withMessage(exception.getMessage()));
                break;
            }
        }
    }

    /**
     * Determines if the import should continue or be aborted because the xml file has to be valid but isn't
     *
     * @param inputSupplier the {@link InputStream} of a xml file which should be validated
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

    protected void executeProcessingStage(InputStream in, Consumer<BiConsumer<String, NodeHandler>> stage)
            throws Exception {
        XMLReader reader = new XMLReader();
        if (importer.getContext().getEventHandler().isActive()) {
            stage.accept((name, originalHandler) -> {
                if (originalHandler.ignoreContent()) {
                    reader.addHandler(name, new NoContentNodeHandler() {
                        @Override
                        public void process(StructuredNode node) {
                            AfterNodeLoadEvent event = new AfterNodeLoadEvent(node, importer.getContext());
                            importer.getContext().getEventHandler().handleEvent(event);
                            originalHandler.process(node);
                        }
                    });
                } else {
                    reader.addHandler(name, node -> {
                        AfterNodeLoadEvent event = new AfterNodeLoadEvent(node, importer.getContext());
                        importer.getContext().getEventHandler().handleEvent(event);
                        originalHandler.process(node);
                    });
                }
            });
        } else {
            stage.accept(reader::addHandler);
        }
        reader.parse(in, this::resolveResource);
    }

    /**
     * Provides a list of stages (or passes) to be performed over a xml file.
     * <p>
     * The xml file will be streamed from beginning for each entry provided. The contents of the list
     * defines a consumer responsible to feed the required handlers for each pass.
     *
     * @return list of handler consumers. Defaults to {@link #registerHandlers(BiConsumer)}
     */
    protected List<Consumer<BiConsumer<String, NodeHandler>>> fetchStages() {
        return Collections.singletonList(this::registerHandlers);
    }

    @Override
    protected boolean canHandleFileExtension(String fileExtension) {
        return "xml".equals(fileExtension);
    }

    /**
     * Registers handlers which are invoked for each appropriate node or subtree parsed by the reader.
     *
     * @param handler the handler to register
     */
    protected abstract void registerHandlers(BiConsumer<String, NodeHandler> handler);

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
