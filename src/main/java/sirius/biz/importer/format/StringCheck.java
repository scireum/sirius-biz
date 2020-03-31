/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.importer.format;

import sirius.kernel.commons.Value;

import javax.annotation.Nullable;

/**
 * Provides a base class for checks on string values.
 * <p>
 * This will provide support for either trimming all string before checking them - which is the default,
 * as all {@link Value} methods do this. However, {@link #checkUntrimmed()} can be used to suppress this behavior.
 */
public abstract class StringCheck implements ValueCheck {

    protected boolean trim = true;

    @Nullable
    protected String determineEffectiveValue(Value value) {
        return trim ? value.getString() : value.getRawString();
    }

    /**
     * Suppresses the automatic trim before checking the value.
     *
     * @return the check itself for fluent method calls
     */
    public StringCheck checkUntrimmed() {
        this.trim = false;
        return this;
    }
}
