/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.jdbc;

import sirius.biz.jobs.JobCategory;
import sirius.biz.jobs.JobFactory;
import sirius.biz.jobs.batch.file.LineBasedExportJob;
import sirius.biz.jobs.batch.file.LineBasedExportJobFactory;
import sirius.biz.jobs.params.Parameter;
import sirius.biz.jobs.params.TextareaParameter;
import sirius.biz.process.ProcessContext;
import sirius.biz.tenants.TenantUserManager;
import sirius.db.jdbc.Database;
import sirius.db.jdbc.Databases;
import sirius.db.jdbc.Row;
import sirius.kernel.commons.Limit;
import sirius.kernel.commons.Monoflop;
import sirius.kernel.commons.Tuple;
import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.Register;
import sirius.web.security.Permission;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.IOException;
import java.util.function.Consumer;

/**
 * Exports all rows of a given SQL query as Excel or CSV file.
 */
@Register(classes = JobFactory.class)
@Permission(TenantUserManager.PERMISSION_SYSTEM_ADMINISTRATOR)
public class ExportQueryResultJobFactory extends LineBasedExportJobFactory {

    private DatabaseParameter databaseParameter = new DatabaseParameter().markRequired();
    private TextareaParameter sqlParameter = new TextareaParameter("query", "Query").markRequired();

    private class ExportQueryResultJob extends LineBasedExportJob {

        private final Database db;
        private final String query;

        private ExportQueryResultJob(ProcessContext process) {
            super(destinationParameter, fileTypeParameter, process);
            this.db = process.require(databaseParameter);
            this.query = process.require(sqlParameter);
        }

        @Override
        protected String determineFilenameWithoutExtension() {
            return "query";
        }

        @Override
        protected void executeIntoExport() throws Exception {
            Monoflop monoflop = Monoflop.create();
            db.createQuery(query).iterateAll(row -> exportRow(row, monoflop), Limit.UNLIMITED);
        }

        private void exportRow(Row row, Monoflop monoflop) {
            try {
                if (monoflop.firstCall()) {
                    export.addRow(Tuple.firsts(row.getFieldsList()));
                }
                export.addRow(Tuple.seconds(row.getFieldsList()));
                process.incCounter("Row");
            } catch (IOException e) {
                throw process.handle(e);
            }
        }
    }

    @Override
    protected void collectParameters(Consumer<Parameter<?, ?>> parameterCollector) {
        parameterCollector.accept(databaseParameter);
        parameterCollector.accept(sqlParameter);

        super.collectParameters(parameterCollector);
    }

    @Override
    protected LineBasedExportJob createJob(ProcessContext process) {
        return new ExportQueryResultJob(process);
    }

    @Override
    public String getLabel() {
        return "Export SQL Query Result";
    }

    @Nullable
    @Override
    public String getDescription() {
        return "Exports all matching rows of a SQL query as an Excel or CSV file.";
    }

    @Override
    public String getCategory() {
        return JobCategory.CATEGORY_MISC;
    }

    @Nonnull
    @Override
    public String getName() {
        return "export-query-result";
    }
}
