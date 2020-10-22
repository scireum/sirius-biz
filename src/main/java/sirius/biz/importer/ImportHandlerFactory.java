/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.importer;

import sirius.kernel.di.std.AutoRegister;
import sirius.kernel.di.std.Priorized;

/**
 * Creates instances of {@link ImportHandler} for a certain type of entities.
 * <p>
 * Each {@link ImportHandler} has to have its own factory which creates instances in
 * {@link ImporterContext#findHandler(Class)} if an appropriate type shows up.
 * <p>
 * A factory has to be made visible to the framework using a {@link sirius.kernel.di.std.Register} annotation. It is
 * common practice to put the factory as static public inner class into the actual import handler as they are tightly
 * coupled anyway.
 */
@AutoRegister
public interface ImportHandlerFactory extends Priorized {

    /**
     * Determines the sort priority of this factory.
     * <p>
     * If the factory handles a whole subtree of classes which might be specialized, a higher value should be chosen,
     * so that specific implementations for certain classes are preferred. However, if this factory handles a single
     * class, the method shouldn't be overloaded, as the default priority is appropriate for this case.
     * <p>
     * If a customization needs to change the behaviour of a default importer, a lower value must be selected,
     * so that it is perferred over the standard.
     *
     * @return the priority used to sort the available factories (ascending)
     */
    @Override
    default int getPriority() {
        return Priorized.DEFAULT_PRIORITY;
    }

    /**
     * Determines if this factory can create an appropriate {@link ImportHandler} for the given type.
     *
     * @param type    the type to check
     * @param context the context of this import
     * @return <tt>true</tt> if {@link #create(Class, ImporterContext)} should be called for the given type
     * in order to create a new handler,<tt>false</tt> otherwise.
     */
    boolean accepts(Class<?> type, ImporterContext context);

    /**
     * Creates a new import handler for the given type and context.
     *
     * @param type    the type of entities to import
     * @param context the context of this import
     * @return a new instance of an appropriate import handler for the given type
     */
    ImportHandler<?> create(Class<?> type, ImporterContext context);
}
