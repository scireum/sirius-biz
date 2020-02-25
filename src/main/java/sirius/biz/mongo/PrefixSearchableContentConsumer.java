/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.mongo;

import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Used as target class to transform {@link sirius.db.mixing.Property properties} into a {@link BiConsumer}.
 * <p>
 * The consumer takes the {@link PrefixSearchableEntity} containing the values to extract which are then
 * passed as the {@link Consumer} argument.
 */
public interface PrefixSearchableContentConsumer extends BiConsumer<PrefixSearchableEntity, Consumer<String>> {

}
