/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.jupiter;

import redis.clients.jedis.util.SafeEncoder;
import sirius.db.redis.Redis;
import sirius.db.redis.RedisDB;
import sirius.kernel.Sirius;
import sirius.kernel.commons.Value;
import sirius.kernel.commons.Values;
import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.Register;
import sirius.kernel.health.Average;
import sirius.kernel.health.Log;

import javax.annotation.Nullable;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Provides a facility to connect to one or more <a href="https://github.com/scireum/jupiter">Jupiter</a> instances.
 * <p>
 * As Jupiter speaks the RESP protocol as defined by Redis, we actually use the connection handling as provided
 * ba {@link Redis}. However, since Jupiter is commonly "stateless" we permit to define a fallback connection
 * in case the main host goes down.
 * <p>
 * The config is provided via <tt>redis.pools.jupiter</tt> as well as the <tt>jupiter.settings.[name]</tt> section in
 * the config.
 */
@Register(classes = Jupiter.class, framework = Jupiter.FRAMEWORK_JUITER)
public class Jupiter {

    /**
     * Specifies the framework to enable when using Jupiter.
     */
    public static final String FRAMEWORK_JUITER = "jupiter";

    /**
     * Determines the name of the logger used for all Jupiter related logging.
     */
    public static final Log LOG = Log.get("jupiter");

    /**
     * Determines the name of the default instance (redis.pools.XXX).
     */
    public static final String DEFAULT_NAME = "jupiter";

    private static final DateTimeFormatter RFC_3339_DATE_TIME_FORMATTER =
            new DateTimeFormatterBuilder().append(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                                          .optionalStart()
                                          .appendOffset("+HH:MM", "Z")
                                          .optionalEnd()
                                          .toFormatter();

    @Part
    private Redis redis;

    protected Average callDuration = new Average();

    private JupiterConnector defaultConnection;
    private final Map<String, RedisDB> fallbackPools = new ConcurrentHashMap<>();

    /**
     * Provides the default connector which is configured via (redis.pools.jupiter).
     *
     * @return a connector for the default instance
     */
    public JupiterConnector getDefault() {
        if (defaultConnection == null) {
            defaultConnection = getConnector(DEFAULT_NAME);
        }

        return defaultConnection;
    }

    /**
     * Provides a connector to the instance with the given name.
     *
     * @param name the name of the connector to connect to.
     * @return the connector to the instance with the given name
     */
    public JupiterConnector getConnector(String name) {
        return new JupiterConnector(this,
                                    name,
                                    redis.getPool(name),
                                    fallbackPools.computeIfAbsent(name, this::fetchFallbackPool));
    }

    @Nullable
    private RedisDB fetchFallbackPool(String name) {
        String fallbackName = Sirius.getSettings().getMap("jupiter.ha").get(name);
        if (fallbackName != null) {
            return redis.getPool(fallbackName);
        } else {
            return null;
        }
    }

    public static String readString(Value value) {
        return SafeEncoder.encode((byte[]) value.get());
    }

    public static Values readArray(Object object) {
        if (object instanceof List) {
            return Values.of((List<?>) object);
        } else {
            return Values.of(Collections.emptyList());
        }
    }

    public static LocalDateTime readLocalDateTime(Value value) {
        return OffsetDateTime.parse(readString(value), RFC_3339_DATE_TIME_FORMATTER)
                             .atZoneSameInstant(ZoneId.systemDefault())
                             .toLocalDateTime();
    }

    public static Object read(Object obj) {
        if (obj instanceof byte[]) {
            return SafeEncoder.encode((byte[]) obj);
        }
        if (obj instanceof List) {
            return ((List<?>) obj).stream().map(Jupiter::read).collect(Collectors.toList());
        }

        return obj;
    }
}
