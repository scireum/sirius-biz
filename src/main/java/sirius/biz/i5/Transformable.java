/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.i5;

import javax.annotation.Nonnull;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static sirius.kernel.health.Exceptions.handle;

/**
 * Base class for all types which can automatically be transformed from and to byte arrays.
 * <p>
 * The {@link Transformer} for the given class will read all {@link Transform} annotations and determine
 * which parts of the byte array belong to which Java field. It then converts them using the appropriate
 * <tt>AS400...</tt> classes.
 */
public abstract class Transformable {

    private static final Map<Class<?>, Transformer> transformers = new ConcurrentHashMap<>();

    private static <T> Transformer getTransformer(@Nonnull Class<T> type) {
        return transformers.computeIfAbsent(type, k -> new Transformer(type));
    }

    /**
     * Tries for create a new instance of the given type by parsing the given byte array.
     *
     * @param type       the type to be created
     * @param <T>        the generic type for <tt>type</tt>
     * @param data       the byte array to parse
     * @param connection the connection used to determine the CCSID (code page) to use
     * @return a new instance of T filled with the data parsed from <tt>data</tt>
     */
    public static <T extends Transformable> T parse(@Nonnull Class<T> type,
                                                    @Nonnull byte[] data,
                                                    I5Connection connection) {
        try {
            Transformer tx = getTransformer(type);
            T result = type.getDeclaredConstructor().newInstance();
            tx.fromBytes(result, data, connection.getCcsid());

            return result;
        } catch (Exception e) {
            throw handle().to(I5Connector.LOG)
                          .error(e)
                          .withSystemErrorMessage("Cannot load data for '%s': %s (%s)", type.getName())
                          .handle();
        }
    }

    /**
     * Transforms this object into a byte representation which can be sent to an i5.
     *
     * @param destination the byte array to fill
     * @param connection the connection used to determine the CCSID (code page) to use
     */
    public void toBytes(byte[] destination, I5Connection connection) {
        getTransformer(getClass()).toBytes(this, destination, connection.getCcsid());
    }

    @Override
    public String toString() {
        return getTransformer(getClass()).asString(this);
    }
}
