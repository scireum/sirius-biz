/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.jobs.batch.file;

import sirius.biz.importer.Importer;
import sirius.biz.importer.format.ImportDictionary;
import sirius.biz.jobs.params.EnumParameter;
import sirius.biz.jobs.params.Parameter;
import sirius.biz.process.ProcessContext;
import sirius.db.mixing.BaseEntity;
import sirius.kernel.commons.Explain;
import sirius.kernel.health.Exceptions;
import sirius.kernel.health.Log;

import java.util.function.Consumer;

/**
 * Provides a base implementation for batch jobs which import line based files using a
 * {@link sirius.biz.importer.ImportHandler}.
 */
public abstract class EntityImportJobFactory extends DictionaryBasedImportJobFactory {

    /**
     * Determines the {@link ImportMode}.
     */
    protected final EnumParameter<ImportMode> importModeParameter;

    protected EntityImportJobFactory() {
        importModeParameter = new EnumParameter<>("importMode", "$EntityImportJobFactory.importMode", ImportMode.class);
        importModeParameter.withDefault(ImportMode.NEW_AND_UPDATES);
        importModeParameter.markRequired();
        importModeParameter.withDescription("$EntityImportJobFactory.importMode.help");
    }

    @Override
    protected abstract EntityImportJob<?> createJob(ProcessContext process);

    /**
     * Creates the dictionary used by the import.
     *
     * @return the dictionary being used
     */
    protected ImportDictionary getDictionary() {
        try (Importer importer = new Importer("getDictionary")) {
            ImportDictionary dictionary = importer.getImportDictionary(getImportType());
            enhanceDictionary(dictionary);
            return dictionary;
        } catch (Exception e) {
            throw Exceptions.handle(Log.BACKGROUND, e);
        }
    }

    /**
     * Returns the main type being imported by this job.
     *
     * @return the type of entities being imported
     */
    protected abstract Class<? extends BaseEntity<?>> getImportType();

    /**
     * Adds the possibility to enhance a dicitonary during the setup of the job
     *
     * @param dictionary the dictionary to enhance
     */
    @SuppressWarnings("squid:S1186")
    @Explain("Do nothing by default since we only need this for imports which contain more than one Entity")
    protected void enhanceDictionary(ImportDictionary dictionary) {
    }

    @Override
    protected void collectParameters(Consumer<Parameter<?, ?>> parameterCollector) {
        super.collectParameters(parameterCollector);
        parameterCollector.accept(importModeParameter);
    }
}
