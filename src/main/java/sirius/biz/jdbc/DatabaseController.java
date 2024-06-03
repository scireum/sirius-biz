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

import java.sql.SQLException;
import java.util.List;
import java.util.function.Function;

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
    private static final String PARAM_EXPORT_DATABASE = "exportDatabase";
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

    @Part
    private DatabaseDisplayUtils databaseDisplayUtils;

    /**
     * Renders the UI to execute SQL queries.
     *
     * @param webContext the current request
     */
    @Permission(TenantUserManager.PERMISSION_SYSTEM_ADMINISTRATOR)
    @Routed("/system/sql")
    @DefaultRoute
    public void sql(WebContext webContext) {
        // Only display selectable databases which are properly configured...
        List<String> availableDatabases =
                selectableDatabases.stream().filter(name -> databases.getDatabases().contains(name)).toList();
        webContext.respondWith().template("/templates/biz/model/sql.html.pasta", availableDatabases, defaultDatabase);
    }

    /**
     * Executes the given sql query.
     *
     * @param webContext the current request
     * @param output     the JSON response
     * @throws SQLException in case of a database error
     */
    @Permission(TenantUserManager.PERMISSION_SYSTEM_ADMINISTRATOR)
    @Routed("/system/sql/api/execute")
    @InternalService
    public void executeQuery(WebContext webContext, JSONStructuredOutput output) throws SQLException {
        Watch w = Watch.start();

        try {
            String database = webContext.get(PARAM_DATABASE).asString(defaultDatabase);
            Database db = determineDatabase(database);
            String sqlStatement = webContext.get(PARAM_QUERY).asString();
            SQLQuery query = db.createQuery(sqlStatement).markAsLongRunning();

            OMA.LOG.INFO("Executing SQL (via /system/sql, authored by %s):%n%n%s",
                         UserContext.getCurrentUser().getUserName(),
                         sqlStatement);

            if (isDDLStatement(sqlStatement)) {
                // To prevent accidental damage, we try to filter DDL queries (modifying the database structure) and
                // only permit them against our system database.
                if (!Strings.areEqual(database, defaultDatabase)) {
                    throw Exceptions.createHandled()
                                    .withSystemErrorMessage(
                                            "Cannot execute a DDL statement against this database. This can be only done for '%s'",
                                            defaultDatabase)
                                    .handle();
                }

                output.property("rowModified", query.executeUpdate());
            } else if (isModifyStatement(sqlStatement)) {
                output.property("rowModified", query.executeUpdate());
            } else {
                Monoflop monoflop = Monoflop.create();
                query.iterateAll(r -> outputRow(output, monoflop, r),
                                 new Limit(0, webContext.get("limit").asInt(DEFAULT_LIMIT)));
                if (monoflop.successiveCall()) {
                    output.endArray();
                }
            }
            output.property("duration", w.duration());
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

        String database = webContext.get(PARAM_EXPORT_DATABASE).asString(defaultDatabase);
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

    private boolean isDDLStatement(String query) {
        String lowerCaseQuery = query.toLowerCase().trim();
        return lowerCaseQuery.startsWith(KEYWORD_ALTER)
               || lowerCaseQuery.startsWith(KEYWORD_DROP)
               || lowerCaseQuery.startsWith(KEYWORD_CREATE);
    }

    private void outputRow(JSONStructuredOutput output, Monoflop monoflop, Row row) {
        if (monoflop.firstCall()) {
            output.beginArray("columns");
            for (Tuple<String, Object> col : row.getFieldsList()) {
                output.property("column", col.getFirst());
            }
            output.endArray();
            output.beginArray("rows");
        }
        output.beginArray("row");
        for (Tuple<String, Object> col : row.getFieldsList()) {
            output.property("column", databaseDisplayUtils.formatValueForDisplay(col.getSecond()));
        }
        output.endArray();
    }

    /**
     * Renders the schema list view.
     *
     * @param webContext the current request
     */
    @Permission(TenantUserManager.PERMISSION_SYSTEM_ADMINISTRATOR)
    @Routed("/system/schema")
    public void changes(WebContext webContext) {
        webContext.respondWith().template("/templates/biz/model/schema.html.pasta");
    }

    /**
     * Lists all required changes as JSON
     *
     * @param webContext the current request
     * @param output     the JSON response
     */
    @Permission(TenantUserManager.PERMISSION_SYSTEM_ADMINISTRATOR)
    @Routed("/system/schema/api/list")
    @InternalService
    public void changesList(WebContext webContext, JSONStructuredOutput output) {
        schema.computeRequiredSchemaChanges();
        output.beginArray("changes");
        for (SchemaUpdateAction action : schema.getSchemaUpdateActions()) {
            output.beginObject("change");
            output.property("id", action.getId());
            output.property("reason", action.getReason());
            output.property("realm", action.getRealm());
            output.property("sql", String.join(";\n", action.getSql()) + ";");
            output.property("executed", action.isExecuted());
            output.property("failed", action.isFailed());
            output.property("error", Value.of(action.getError()).asString());
            output.property("dataLossPossible", action.isDataLossPossible());
            output.endObject();
        }
        output.endArray();
    }

    /**
     * Executes the given schema change.
     *
     * @param webContext the current request
     * @param output     the JSON response
     */
    @Permission(TenantUserManager.PERMISSION_SYSTEM_ADMINISTRATOR)
    @Routed("/system/schema/api/execute")
    @InternalService
    public void execute(WebContext webContext, JSONStructuredOutput output) {
        SchemaUpdateAction result = schema.executeSchemaUpdateAction(webContext.get("id").asString());

        if (result != null) {
            output.property("errorMessage", Value.of(result.getError()).asString());
        } else {
            output.property("errorMessage", NLS.get("DatabaseController.unknownChange"));
        }
    }
}
