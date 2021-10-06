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
 * Used as transformation target for {@link sirius.db.mixing.Property properties} in
 * {@link PrefixSearchableEntity searchable entities} to provide the actual search content to store.
 */
public interface PrefixSearchableContentComputer extends BiConsumer<PrefixSearchableEntity, Consumer<Object>> {

}
