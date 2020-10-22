/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.translations;

/**
 * Provides an interface for entities whose fields can be translated to other languages.
 *
 * @param <T> the {@link BasicTranslations translations} type used by a concrete subclass
 */
public interface Translatable<T extends BasicTranslations<?>> {

    /**
     * Returns available translations for the entity implementing this interface.
     *
     * @return available translations for the translatable entity
     */
    T getTranslations();
}
