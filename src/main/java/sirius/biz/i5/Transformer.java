/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.i5;

import com.ibm.as400.access.AS400Bin4;
import com.ibm.as400.access.AS400PackedDecimal;
import com.ibm.as400.access.AS400Text;
import com.ibm.as400.access.AS400ZonedDecimal;
import sirius.kernel.health.Exceptions;

import javax.annotation.Nonnull;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Helps to transform byte oriented record (sent and received from the i5) into Java objects.
 * <p>
 * This is achieved by placing {@link Transform} annotations on fields with appropriate <tt>AS400...</tt> types. These
 * are scanned and automatically copied to or from the byte array.
 */
public class Transformer {

    private final Map<Field, Transform> transforms = new LinkedHashMap<>();

    Transformer(Class<?> target) {
        List<Field> result = new ArrayList<>();
        for (Field field : target.getDeclaredFields()) {
            if (field.isAnnotationPresent(Transform.class)) {
                field.setAccessible(true);
                result.add(field);
            }
        }
        result.sort(Comparator.comparingInt(f -> f.getAnnotation(Transform.class).position()));
        for (Field field : result) {
            transforms.put(field, field.getAnnotation(Transform.class));
        }
    }

    /**
     * Filles the given object from the given byte array.
     *
     * @param object the object to fill
     * @param data   the received byte array
     * @param ccsid  the CCSID (code page) to use. This is best determined via {@link I5Connection#getCcsid()}
     */
    public void fromBytes(@Nonnull Object object, byte[] data, int ccsid) {
        int offset = 0;
        for (Map.Entry<Field, Transform> e : transforms.entrySet()) {
            offset = transform(e, object, ccsid, data, offset);
        }
    }

    /**
     * Fills the byte array from the given object.
     *
     * @param object      the object to take data from
     * @param destination the byte array to fill. Note that this has to have the correct length already.
     * @param ccsid       the CCSID (code page) to use. This is best determined via {@link I5Connection#getCcsid()}
     */
    public void toBytes(@Nonnull Object object, byte[] destination, int ccsid) {
        int offset = 0;
        for (Map.Entry<Field, Transform> e : transforms.entrySet()) {
            offset = serialize(e, object, ccsid, destination, offset);
        }
    }

    private int serialize(Entry<Field, Transform> e, Object object, int ccsid, byte[] data, int offset) {
        Transform info = e.getValue();
        Field field = e.getKey();
        try {
            Object value = field.get(object);
            if (info.targetType() == AS400Text.class) {
                AS400Text mapper = new AS400Text(info.length(), ccsid);
                mapper.toBytes(value == null ? "" : value, data, offset);
                return offset + mapper.getByteLength();
            }
            if (info.targetType() == AS400ZonedDecimal.class) {
                AS400ZonedDecimal mapper = new AS400ZonedDecimal(info.length(), info.decimal());
                mapper.toBytes(value == null ? 0d : ((BigDecimal) value).doubleValue(), data, offset);
                return offset + mapper.getByteLength();
            }
            if (info.targetType() == Byte.class) {
                if (value != null) {
                    //noinspection SuspiciousSystemArraycopy
                    System.arraycopy(value, 0, data, offset, info.length());
                }
                return offset + info.length();
            }
            throw Exceptions.handle()
                            .to(I5Connector.LOG)
                            .withSystemErrorMessage("Cannot transform a field from type: %s (%s.%s)",
                                                    info.targetType().getName(),
                                                    object.getClass().getName(),
                                                    field.getName())
                            .handle();
        } catch (Exception ex) {
            throw Exceptions.handle()
                            .to(I5Connector.LOG)
                            .error(ex)
                            .withSystemErrorMessage("Error while transforming '%s.%s' from %s: %s (%s)",
                                                    object.getClass().getName(),
                                                    field.getName(),
                                                    info.targetType().getName())
                            .handle();
        }
    }

    private int transform(Entry<Field, Transform> e, Object object, int ccsid, byte[] data, int offset) {
        Transform info = e.getValue();
        Field field = e.getKey();
        AtomicInteger nextIndex = new AtomicInteger(offset);
        try {
            if (info.targetType() == AS400Bin4.class) {
                AS400Bin4 mapper = new AS400Bin4();
                nextIndex.addAndGet(mapper.getByteLength());
                field.set(object, mapper.toObject(data, offset));
            } else if (info.targetType() == AS400Text.class) {
                AS400Text mapper = new AS400Text(info.length(), ccsid);
                nextIndex.addAndGet(mapper.getByteLength());
                field.set(object, ((String) mapper.toObject(data, offset)).trim());
            } else if (info.targetType() == AS400ZonedDecimal.class) {
                AS400ZonedDecimal mapper = new AS400ZonedDecimal(info.length(), info.decimal());
                nextIndex.addAndGet(mapper.getByteLength());
                field.set(object, mapper.toObject(data, offset));
            } else if (info.targetType() == AS400PackedDecimal.class) {
                AS400PackedDecimal mapper = new AS400PackedDecimal(info.length(), info.decimal());
                nextIndex.addAndGet(mapper.getByteLength());
                field.set(object, mapper.toObject(data, offset));
            } else if (info.targetType() == Byte.class) {
                nextIndex.addAndGet(info.length());
                field.set(object, Arrays.copyOfRange(data, offset, info.length()));
            } else {
                throw Exceptions.handle()
                                .to(I5Connector.LOG)
                                .withSystemErrorMessage("Cannot transform a field to type: %s (%s.%s)",
                                                        info.targetType().getName(),
                                                        object.getClass().getName(),
                                                        field.getName())
                                .handle();
            }
        } catch (Exception ex) {
            if (info.ignoreErrors()) {
                I5Connector.LOG.FINE("Ignoring a conversion error for %s of %s: %s (%s)",
                                     field.getName(),
                                     object.getClass().getName(),
                                     ex.getMessage(),
                                     ex.getClass().getName());
                Exceptions.ignore(ex);
            } else {
                throw Exceptions.handle()
                                .to(I5Connector.LOG)
                                .error(ex)
                                .withSystemErrorMessage("Error while transforming '%s.%s' to %s: %s (%s)",
                                                        object.getClass().getName(),
                                                        field.getName(),
                                                        info.targetType().getName())
                                .handle();
            }
        }

        return nextIndex.get();
    }

    /**
     * Creates a string representation of the given object by dumping all known fields.
     *
     * @param transformable the object to convert to a string
     * @return a string representation of the given object
     */
    protected String asString(@Nonnull Transformable transformable) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<Field, Transform> e : transforms.entrySet()) {
            try {
                sb.append(e.getKey().getName());
                sb.append(": ");
                sb.append(e.getKey().get(transformable));
                sb.append("\n");
            } catch (Exception t) {
                Exceptions.ignore(t);
            }
        }
        return sb.toString();
    }
}
