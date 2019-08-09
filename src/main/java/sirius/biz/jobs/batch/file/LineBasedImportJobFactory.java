/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.jobs.batch.file;

import sirius.biz.importer.Importer;
import sirius.biz.importer.format.FieldDefinition;
import sirius.biz.importer.format.ImportDictionary;
import sirius.biz.jobs.infos.JobInfoCollector;
import sirius.biz.jobs.params.BooleanParameter;
import sirius.biz.jobs.params.Parameter;
import sirius.biz.process.ProcessContext;
import sirius.biz.storage.VirtualObject;
import sirius.db.mixing.BaseEntity;
import sirius.kernel.commons.Explain;
import sirius.kernel.health.Exceptions;
import sirius.kernel.health.Log;
import sirius.kernel.nls.NLS;
import sirius.web.data.LineBasedProcessor;

import java.util.function.Consumer;

/**
 * Provides a base implementation for batch jobs which import line based files using a {@link LineBasedImportJob}.
 */
public abstract class LineBasedImportJobFactory extends FileImportJobFactory {

    /**
     * Contains the parameter which is used to determine if empty values should be ignored).
     */
    protected final BooleanParameter ignoreEmptyParameter =
            new BooleanParameter("ignoreEmpty", "$LineBasedImportJobFactory.ignoreEmpty").withDescription(
                    "$LineBasedImportJobFactory.ignoreEmpty.help");

    @Override
    protected abstract LineBasedImportJob<?> createJob(ProcessContext process);

    /**
     * Creates the dictionary used by the import.
     *
     * @return the dictionary being used
     */
    protected ImportDictionary getDictionary() {
        try (Importer importer = new Importer("getDictionary")) {
            ImportDictionary dictionary = importer.getDictionary(getImportType());
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
    protected void collectJobInfos(JobInfoCollector collector) {
        super.collectJobInfos(collector);
        collector.addTranslatedHeading("ImportDictionary.fields");
        collector.addReport((report, cells) -> {
            report.addColumn("name", NLS.get("FieldDefinition.name"));
            report.addColumn("type", NLS.get("FieldDefinition.type"));
            report.addColumn("remarks", NLS.get("FieldDefinition.remarks"));

            for (FieldDefinition field : getDictionary().getFields()) {
                report.addCells(cells.of(field.getLabel()), cells.of(field.getType()), cells.list(field.getRemarks()));
            }
        });
        collector.addTranslatedWell("ImportDictionary.automaticMappings");
    }

    /**
     * Uses the given file and creates a {@link LineBasedProcessor} for it.
     *
     * @param ctx the current process context
     * @return the processor which is appropriate to read the given file
     */
    protected LineBasedProcessor createProcessor(ProcessContext ctx) {
        VirtualObject file = ctx.require(fileParameter);
        return LineBasedProcessor.create(file.getFilename(), storage.getData(file));
    }

    @Override
    protected void collectParameters(Consumer<Parameter<?, ?>> parameterCollector) {
        super.collectParameters(parameterCollector);
        parameterCollector.accept(ignoreEmptyParameter);
    }

    @Override
    public String getIcon() {
        return "fa-file-excel-o";
    }
}
