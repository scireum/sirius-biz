/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.jdbc;

import sirius.biz.jobs.Jobs;
import sirius.biz.tenants.TenantUserManager;
import sirius.db.jdbc.Database;
import sirius.db.jdbc.Databases;
import sirius.db.jdbc.OMA;
import sirius.db.jdbc.Row;
import sirius.db.jdbc.SQLQuery;
import sirius.db.jdbc.schema.Schema;
import sirius.db.jdbc.schema.SchemaUpdateAction;
import sirius.kernel.commons.Limit;
import sirius.kernel.commons.Monoflop;
import sirius.kernel.commons.Strings;
import sirius.kernel.commons.Tuple;
import sirius.kernel.commons.Value;
import sirius.kernel.commons.Watch;
import sirius.kernel.di.std.ConfigValue;
import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.Register;
import sirius.kernel.health.Exceptions;
import sirius.kernel.nls.NLS;
import sirius.web.controller.BasicController;
import sirius.web.controller.DefaultRoute;
import sirius.web.controller.Routed;
import sirius.web.http.WebContext;
import sirius.web.security.Permission;
import sirius.web.security.UserContext;
import sirius.web.services.InternalService;
import sirius.web.services.JSONStructuredOutput;

import javax.annotation.Nullable;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Provides the management GUI for database related activities.
 */
@Register
public class DatabaseController extends BasicController {

    /**
     * Contains the default limit to prevent accidents when querying large tables
     */
    private static final int DEFAULT_LIMIT = 1000;

    private static final String PARAM_DATABASE = "database";
    private static final String PARAM_QUERY = "query";
    private static final String PARAM_EXPORT_QUERY = "exportQuery";
    private static final String KEYWORD_UPDATE = "update";
    private static final String KEYWORD_INSERT = "insert";
    private static final String KEYWORD_ALTER = "alter";
    private static final String KEYWORD_DROP = "drop";
    private static final String KEYWORD_CREATE = "create";
    private static final String KEYWORD_DELETE = "delete";

    @Part
    private Schema schema;

    @Part
    private Databases databases;

    @ConfigValue("mixing.jdbc.mixing.database")
    private String defaultDatabase;

    @ConfigValue("jdbc.selectableDatabases")
    private List<String> selectableDatabases;

    @Part
    private Jobs jobs;

    /**
     * Renders the UI to execute SQL queries.
     *
     * @param ctx the current request
     */
    @Permission(TenantUserManager.PERMISSION_SYSTEM_ADMINISTRATOR)
    @Routed("/system/sql")
    @DefaultRoute
    public void sql(WebContext ctx) {
        // Only display selectable databases which are properly configured..
        List<String> availableDatabases = selectableDatabases.stream()
                                                             .filter(name -> databases.getDatabases().contains(name))
                                                             .collect(Collectors.toList());
        ctx.respondWith().template("/templates/biz/model/sql.html.pasta", availableDatabases, defaultDatabase);
    }

    /**
     * Executes the given sql query.
     *
     * @param webContext the current request
     * @param out        the JSON response
     * @throws SQLException in case of a database error
     */
    @Permission(TenantUserManager.PERMISSION_SYSTEM_ADMINISTRATOR)
    @Routed("/system/sql/api/execute")
    @InternalService
    public void executeQuery(WebContext webContext, JSONStructuredOutput out) throws SQLException {
        Watch w = Watch.start();

        try {
            String database = webContext.get(PARAM_DATABASE).asString(defaultDatabase);
            Database db = determineDatabase(database);
            String sqlStatement = webContext.get(PARAM_QUERY).asString();
            SQLQuery qry = db.createQuery(sqlStatement).markAsLongRunning();

            OMA.LOG.INFO("Executing SQL (via /system/sql, authored by %s): %s",
                         UserContext.getCurrentUser().getUserName(),
                         sqlStatement);

            if (isDDLStatement(sqlStatement)) {
                // To prevent accidential damage, we try to filter DDL queries (modifying the database structure) and
                // only permit them against our system database.
                if (!Strings.areEqual(database, defaultDatabase)) {
                    throw Exceptions.createHandled()
                                    .withSystemErrorMessage(
                                            "Cannot execute a DDL statement against this database. This can be only done for '%s'",
                                            database)
                                    .handle();
                }

                out.property("rowModified", qry.executeUpdate());
            } else if (isModifyStatement(sqlStatement)) {
                out.property("rowModified", qry.executeUpdate());
            } else {
                Monoflop monoflop = Monoflop.create();
                qry.iterateAll(r -> outputRow(out, monoflop, r),
                               new Limit(0, webContext.get("limit").asInt(DEFAULT_LIMIT)));
                if (monoflop.successiveCall()) {
                    out.endArray();
                }
            }
            out.property("duration", w.duration());
        } catch (SQLException exception) {
            // In case of an invalid query, we do not want to log this into the syslog but
            // rather just directly output the message to the user....
            throw Exceptions.createHandled().error(exception).withDirectMessage(exception.getMessage()).handle();
        }
    }

    /**
     * Exports the given SQL query
     *
     * @param webContext the current request
     */
    @Permission(TenantUserManager.PERMISSION_SYSTEM_ADMINISTRATOR)
    @Routed("/system/sql/export")
    public void exportQuery(WebContext webContext) {
        if (!webContext.isSafePOST()) {
            throw Exceptions.createHandled().withSystemErrorMessage("Unsafe or missing POST detected!").handle();
        }

        String database = webContext.get(PARAM_DATABASE).asString(defaultDatabase);
        String sqlStatement = webContext.get(PARAM_EXPORT_QUERY).asString();

        if (isDDLStatement(sqlStatement)) {
            throw Exceptions.createHandled().withDirectMessage("A DDL statement cannot be exported.").handle();
        } else if (isModifyStatement(sqlStatement)) {
            throw Exceptions.createHandled().withDirectMessage("A modifying statement cannot be exported.").handle();
        } else {
            ExportQueryResultJobFactory jobFactory =
                    jobs.findFactory(ExportQueryResultJobFactory.FACTORY_NAME, ExportQueryResultJobFactory.class);
            String processId = jobFactory.startInBackground(createJobParameterSupplier(database, sqlStatement));
            webContext.respondWith().redirectToGet("/ps/" + processId);
        }
    }

    /**
     * Transforms the parameters from the names used here to the ones expected by {@link ExportQueryResultJobFactory}.
     *
     * @param database     the selected database
     * @param sqlStatement the query to execute
     * @return a parameter supplier as expected by the job factory
     */
    private Function<String, Value> createJobParameterSupplier(String database, String sqlStatement) {
        return parameterName -> {
            return switch (parameterName) {
                case PARAM_DATABASE -> Value.of(database);
                case PARAM_QUERY -> Value.of(sqlStatement);
                default -> Value.EMPTY;
            };
        };
    }

    protected Database determineDatabase(String database) {
        if (!selectableDatabases.contains(database)) {
            throw Exceptions.createHandled().withSystemErrorMessage("Unknown database: %s", database).handle();
        }
        return databases.get(database);
    }

    private boolean isModifyStatement(String query) {
        String lowerCaseQuery = query.toLowerCase().trim();
        return lowerCaseQuery.startsWith(KEYWORD_UPDATE)
               || lowerCaseQuery.startsWith(KEYWORD_INSERT)
               || lowerCaseQuery.startsWith(KEYWORD_DELETE);
    }

    private boolean isDDLStatement(String qry) {
        String lowerCaseQuery = qry.toLowerCase().trim();
        return lowerCaseQuery.startsWith(KEYWORD_ALTER)
               || lowerCaseQuery.startsWith(KEYWORD_DROP)
               || lowerCaseQuery.startsWith(KEYWORD_CREATE);
    }

    private void outputRow(JSONStructuredOutput out, Monoflop monoflop, Row row) {
        if (monoflop.firstCall()) {
            out.beginArray("columns");
            for (Tuple<String, Object> col : row.getFieldsList()) {
                out.property("column", col.getFirst());
            }
            out.endArray();
            out.beginArray("rows");
        }
        out.beginArray("row");
        for (Tuple<String, Object> col : row.getFieldsList()) {
            out.property("column", formatValue(col.getSecond()));
        }
        out.endArray();
    }

    private String formatValue(@Nullable Object value) {
        if (value == null) {
            return "";
        }

        if (value.getClass().isArray()) {
            return Arrays.stream((Object[]) value).map(NLS::toUserString).collect(Collectors.joining(", "));
        }

        return NLS.toUserString(value);
    }

    /**
     * Renders the schema list view.
     *
     * @param ctx the current request
     */
    @Permission(TenantUserManager.PERMISSION_SYSTEM_ADMINISTRATOR)
    @Routed("/system/schema")
    public void changes(WebContext ctx) {
        ctx.respondWith().template("/templates/biz/model/schema.html.pasta");
    }

    /**
     * Lists all required changes as JSON
     *
     * @param ctx the current request
     * @param out the JSON response
     */
    @Permission(TenantUserManager.PERMISSION_SYSTEM_ADMINISTRATOR)
    @Routed("/system/schema/api/list")
    @InternalService
    public void changesList(WebContext ctx, JSONStructuredOutput out) {
        schema.computeRequiredSchemaChanges();
        out.beginArray("changes");
        for (SchemaUpdateAction action : schema.getSchemaUpdateActions()) {
            out.beginObject("change");
            out.property("id", action.getId());
            out.property("reason", action.getReason());
            out.property("realm", action.getRealm());
            out.property("sql", String.join(";\n", action.getSql()) + ";");
            out.property("executed", action.isExecuted());
            out.property("failed", action.isFailed());
            out.property("error", Value.of(action.getError()).asString());
            out.property("datalossPossible", action.isDataLossPossible());
            out.endObject();
        }
        out.endArray();
    }

    /**
     * Executes the given schema change.
     *
     * @param ctx the current request
     * @param out the JSON response
     */
    @Permission(TenantUserManager.PERMISSION_SYSTEM_ADMINISTRATOR)
    @Routed("/system/schema/api/execute")
    @InternalService
    public void execute(WebContext ctx, JSONStructuredOutput out) {
        SchemaUpdateAction result = schema.executeSchemaUpdateAction(ctx.get("id").asString());

        if (result != null) {
            out.property("errorMessage", Value.of(result.getError()).asString());
        } else {
            out.property("errorMessage", NLS.get("DatabaseController.unknownChange"));
        }
    }
}
