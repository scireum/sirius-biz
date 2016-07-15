/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.i5;

import com.ibm.as400.access.AS400Bin4;
import com.ibm.as400.access.AS400Text;
import com.ibm.as400.access.AS400ZonedDecimal;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a field as auto transformed by the {@link Transformer}.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface Transform {

    /**
     * Byte position in the record. This has to be filled correctly, as the order of fields in a Java class is
     * undetermined.
     */
    int position();

    /**
     * AS400 target type.
     *
     * @see AS400Text
     * @see AS400ZonedDecimal
     * @see AS400Bin4
     */
    Class<?> targetType();

    /**
     * Length in the byte array.
     */
    int length() default 0;

    /**
     * Number of decimal places for AS400ZonedDecimal
     */
    int decimal() default 0;
}
