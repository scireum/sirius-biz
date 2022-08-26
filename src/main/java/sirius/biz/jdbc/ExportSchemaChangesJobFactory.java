/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.jdbc;

import sirius.biz.jobs.StandardJobCategories;
import sirius.biz.jobs.batch.SimpleBatchProcessJobFactory;
import sirius.biz.jobs.params.Parameter;
import sirius.biz.process.PersistencePeriod;
import sirius.biz.process.ProcessContext;
import sirius.biz.process.Processes;
import sirius.biz.process.logs.ProcessLog;
import sirius.biz.tenants.TenantUserManager;
import sirius.db.jdbc.schema.Schema;
import sirius.db.jdbc.schema.SchemaUpdateAction;
import sirius.kernel.commons.Explain;
import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.Register;
import sirius.web.security.Permission;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Exports all required schema changes as defined by {@link Schema#getSchemaUpdateActions()}.
 */
@Register(framework = Processes.FRAMEWORK_PROCESSES)
@Permission(TenantUserManager.PERMISSION_SYSTEM_ADMINISTRATOR)
public class ExportSchemaChangesJobFactory extends SimpleBatchProcessJobFactory {

    @Part
    private Schema schema;

    @SuppressWarnings("resource")
    @Explain("The writers are closed elsewhere as they are re-used.")
    @Override
    protected void execute(ProcessContext process) throws Exception {
        process.log("Computing changes...");
        schema.computeRequiredSchemaChanges();
        process.log("Exporting changes...");
        Map<String, PrintWriter> writers = new HashMap<>();
        try {
            for (SchemaUpdateAction action : schema.getSchemaUpdateActions()) {
                PrintWriter writer = writers.computeIfAbsent(action.getRealm(), realm -> createWriter(process, realm));
                action.getSql().stream().map(sql -> sql + ";\n").forEach(writer::print);
            }
        } finally {
            writers.values().forEach(writer -> safeCloseWriter(writer, process));
        }
    }

    private void safeCloseWriter(PrintWriter writer, ProcessContext processContext) {
        try {
            writer.close();
        } catch (Exception e) {
            processContext.handle(e);
        }
    }

    @Nonnull
    private PrintWriter createWriter(ProcessContext process, String realm) {
        try {
            process.log(ProcessLog.info().withFormattedMessage("Creating export for realm '%s'", realm));
            return new PrintWriter(new OutputStreamWriter(process.addFile(realm + ".sql")));
        } catch (IOException e) {
            throw process.handle(e);
        }
    }

    @Override
    protected String createProcessTitle(Map<String, String> context) {
        return getLabel();
    }

    @Override
    protected void collectParameters(Consumer<Parameter<?>> parameterCollector) {
        // No parameters required
    }

    @Override
    public String getLabel() {
        return "Export SQL Schema Changes";
    }

    @Nullable
    @Override
    public String getDescription() {
        return "Exports all required Schema changes for the JDBC Databases known to Mixing.";
    }

    @Override
    public String getCategory() {
        return StandardJobCategories.SYSTEM_ADMINISTRATION;
    }

    @Override
    protected PersistencePeriod getPersistencePeriod() {
        return PersistencePeriod.FOURTEEN_DAYS;
    }

    @Nonnull
    @Override
    public String getName() {
        return "export-schema-changes";
    }
}
