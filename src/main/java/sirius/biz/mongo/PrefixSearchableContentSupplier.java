/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.mongo;

import java.util.function.Function;

/**
 * Used as target class to transform {@link sirius.db.mixing.Property properties} values to other formats.
 * <p>
 * This is used by {@link PrefixSearchableEntity} in conjunction with a {@link sirius.biz.i5.Transformer} to override
 * the valued delivered by {@link sirius.db.mixing.Property#getValue(Object)} where applicable.
 */
public interface PrefixSearchableContentSupplier extends Function<PrefixSearchableEntity, Object> {

}
