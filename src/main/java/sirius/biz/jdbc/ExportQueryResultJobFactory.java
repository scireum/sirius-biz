/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.jdbc;

import sirius.biz.jobs.StandardCategories;
import sirius.biz.jobs.batch.file.LineBasedExportJob;
import sirius.biz.jobs.batch.file.LineBasedExportJobFactory;
import sirius.biz.jobs.params.Parameter;
import sirius.biz.jobs.params.TextareaParameter;
import sirius.biz.process.ProcessContext;
import sirius.biz.process.Processes;
import sirius.biz.tenants.TenantUserManager;
import sirius.db.jdbc.Database;
import sirius.db.jdbc.Row;
import sirius.kernel.commons.Limit;
import sirius.kernel.commons.Monoflop;
import sirius.kernel.commons.Tuple;
import sirius.kernel.di.std.Register;
import sirius.kernel.health.Exceptions;
import sirius.web.security.Permission;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.IOException;
import java.sql.SQLException;
import java.util.function.Consumer;

/**
 * Exports all rows of a given SQL query as Excel or CSV file.
 */
@Register(framework = Processes.FRAMEWORK_PROCESSES)
@Permission(TenantUserManager.PERMISSION_SYSTEM_ADMINISTRATOR)
public class ExportQueryResultJobFactory extends LineBasedExportJobFactory {

    /**
     * Contains the part-name of this factory.
     */
    public static final String FACTORY_NAME = "export-query-result";
    
    private final Parameter<Database> databaseParameter = new DatabaseParameter().markRequired().build();
    private final Parameter<String> sqlParameter = new TextareaParameter("query", "Query").markRequired().build();

    private class ExportQueryResultJob extends LineBasedExportJob {

        private final Database db;
        private final String query;

        private ExportQueryResultJob(ProcessContext process) {
            super(process);
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
            try {
                db.createQuery(query).markAsLongRunning().iterateAll(row -> exportRow(row, monoflop), Limit.UNLIMITED);
            } catch (SQLException exception) {
                // In case of an invalid query, we do not want to log this into the syslog but
                // rather just directly output the message to the user....
                throw Exceptions.createHandled().error(exception).withDirectMessage(exception.getMessage()).handle();
            }
        }

        private void exportRow(Row row, Monoflop monoflop) {
            try {
                if (monoflop.firstCall()) {
                    export.addListRow(Tuple.firsts(row.getFieldsList()));
                }
                export.addListRow(Tuple.seconds(row.getFieldsList()));
                process.incCounter("Row");
            } catch (IOException e) {
                throw process.handle(e);
            }
        }
    }

    @Override
    protected void collectParameters(Consumer<Parameter<?>> parameterCollector) {
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
        return StandardCategories.SYSTEM_ADMINISTRATION;
    }

    @Nonnull
    @Override
    public String getName() {
        return FACTORY_NAME;
    }

    @Override
    public int getPriority() {
        return 10100;
    }
}
