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
import sirius.biz.jobs.params.Parameter;
import sirius.biz.process.ProcessContext;
import sirius.biz.storage.layer3.FileParameter;
import sirius.db.mixing.BaseEntity;
import sirius.kernel.commons.Explain;
import sirius.kernel.health.Exceptions;
import sirius.kernel.health.Log;
import sirius.kernel.nls.NLS;

import java.util.List;
import java.util.function.Consumer;

/**
 * Provides a base implementation for batch jobs which export entities into line based files using a
 * {@link EntityExportJob}.
 */
public abstract class EntityExportJobFactory extends LineBasedExportJobFactory {

    protected final FileParameter templateFileParameter;

    protected EntityExportJobFactory() {
        templateFileParameter = new FileParameter("templateFile", "$EntityExportJobFactory.templateFile");
        templateFileParameter.withDescription("$EntityExportJobFactory.templateFile.help");
        templateFileParameter.withBasePath("/work");
    }

    @Override
    protected void collectParameters(Consumer<Parameter<?, ?>> parameterCollector) {
        super.collectParameters(parameterCollector);
        parameterCollector.accept(templateFileParameter);
    }

    @Override
    protected abstract EntityExportJob<?> createJob(ProcessContext process);

    /**
     * Creates the dictionary used by the export.
     *
     * @return the dictionary being used
     */
    protected ImportDictionary getDictionary() {
        try (Importer importer = new Importer("getDictionary")) {
            ImportDictionary dictionary = importer.getExportDictionary(getExportType());
            enhanceDictionary(dictionary);
            return dictionary;
        } catch (Exception e) {
            throw Exceptions.handle(Log.BACKGROUND, e);
        }
    }

    /**
     * Defines the default list of export columns.
     *
     * @return the columns to export unless a custom list is given via a template file
     */
    protected List<String> getDefaultMapping() {
        try (Importer importer = new Importer("getDefaultMapping")) {
            return importer.findHandler(getExportType()).getDefaultExportMapping();
        } catch (Exception e) {
            throw Exceptions.handle(Log.BACKGROUND, e);
        }
    }

    /**
     * Returns the main type being exported by this job.
     *
     * @return the type of entities being imported
     */
    protected abstract Class<? extends BaseEntity<?>> getExportType();

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
        collector.addTranslatedWell("EntityExportJobFactory.templateModes");
    }
}
