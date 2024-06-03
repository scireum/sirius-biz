/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.scripting;

import sirius.biz.process.ErrorContext;
import sirius.pasta.noodle.sandbox.NoodleSandbox;

/**
 * Provides a base class for all custom events which can be internally typed to a specified type.
 * <p>
 * This might be used to events which are the same for many classed (e.g. update events for entities).
 *
 * @param <T> the type of object for which this event occurred.
 */
public abstract class TypedScriptableEvent<T> extends ScriptableEvent {

    /**
     * The type of object for which this event occurred.
     *
     * @return the type of object for which this event occurred
     */
    public abstract Class<T> getType();

    /**
     * Returns the error context which can be used to log errors or warnings respecting the current import context.
     * <p>
     * Most likely you will use something like this inside a script:
     * {@code log(getErrorContext().enhanceMessage("Something went wrong"));}
     *
     * @return the error context to use
     */
    @NoodleSandbox(NoodleSandbox.Accessibility.GRANTED)
    public ErrorContext getErrorContext() {
        return ErrorContext.get();
    }
}
