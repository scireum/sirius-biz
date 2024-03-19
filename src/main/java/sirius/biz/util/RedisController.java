/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.util;

import redis.clients.jedis.util.SafeEncoder;
import sirius.biz.tenants.TenantUserManager;
import sirius.biz.web.BizController;
import sirius.db.redis.Redis;
import sirius.db.redis.RedisDB;
import sirius.kernel.commons.CommandParser;
import sirius.kernel.commons.Strings;
import sirius.kernel.commons.Watch;
import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.Register;
import sirius.kernel.health.Exceptions;
import sirius.kernel.nls.NLS;
import sirius.web.controller.Routed;
import sirius.web.http.WebContext;
import sirius.web.security.Permission;
import sirius.web.services.InternalService;
import sirius.web.services.JSONStructuredOutput;

import java.util.List;

/**
 * Provides the management GUI for Redis related activities.
 */
@Register
public class RedisController extends BizController {

    public static final String[] EMPTY_STRING_ARRAY = new String[0];
    @Part
    private Redis redis;

    /**
     * Renders the query template.
     *
     * @param webContext the request to respond to
     */
    @Permission(TenantUserManager.PERMISSION_SYSTEM_ADMINISTRATOR)
    @Routed("/system/redis")
    public void redis(WebContext webContext) {
        webContext.respondWith().template("/templates/biz/model/redis.html.pasta", redis.getPools(), Redis.POOL_SYSTEM);
    }

    /**
     * Executes the given Redis query.
     *
     * @param webContext the current request
     * @param output     the JSON response
     */
    @Permission(TenantUserManager.PERMISSION_SYSTEM_ADMINISTRATOR)
    @Routed("/system/redis/api/execute")
    @InternalService
    public void executeQuery(WebContext webContext, JSONStructuredOutput output) {
        Watch watch = Watch.start();

        String database = webContext.get("pool").asString(Redis.POOL_SYSTEM);
        RedisDB pool = redis.getPool(database);
        String query = webContext.get("query").asString();

        Object result = pool.query(() -> "Executing query via /system/redis", db -> {
            try {
                CommandParser parser = new CommandParser(query);
                db.getClient().sendCommand(() -> SafeEncoder.encode(parser.parseCommand()), parser.getArgArray());

                return db.getClient().getOne();
            } catch (Exception exception) {
                // In case of an invalid query, we do not want to log this into the syslog but
                // rather just directly output the message to the user....
                throw Exceptions.createHandled().error(exception).withDirectMessage(exception.getMessage()).handle();
            }
        });
        StringBuilder resultBuilder = new StringBuilder();
        renderResult(result, "", resultBuilder);
        output.property("result", resultBuilder.toString());
        output.property("duration", watch.duration());
    }

    private void renderResult(Object result, String offset, StringBuilder resultBuilder) {
        if (result instanceof List<?> results) {
            for (int i = 0; i < results.size(); i++) {
                if (i > 0) {
                    resultBuilder.append(offset);
                }
                resultBuilder.append(Strings.apply("%2d", i + 1));
                resultBuilder.append(") ");
                renderResult(results.get(i), offset + "    ", resultBuilder);
            }
        } else if (result instanceof byte[] byteArray) {
            resultBuilder.append(SafeEncoder.encode(byteArray));
            resultBuilder.append("\n");
        } else {
            resultBuilder.append(NLS.toUserString(result));
            resultBuilder.append("\n");
        }
    }
}
