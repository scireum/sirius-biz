/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.jobs.params;

import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.Priorized;
import sirius.kernel.nls.NLS;

import java.util.Collection;

/**
 * Provides the selection of a {@link Part} from a list of parts with a common {@link sirius.kernel.di.std.Register registered} superclass as parameter.
 *
 * @param <E> the common {@link sirius.kernel.di.std.Register registered} superclass
 */
public class PriorizedPartListParameter<E extends Priorized> extends PartListParameter<E> {

    /**
     * Creates a new parameter with the given name and label.
     *
     * @param name  the name of the parameter
     * @param label the label of the parameter, which will be {@link NLS#smartGet(String) auto translated}
     * @param type  the type of parts being fetched
     */
    public PriorizedPartListParameter(String name, String label, Class<E> type) {
        super(name, label, type);
    }

    /**
     * Enumerates all parts implementing the common superclass part.
     *
     * @return the list of parts implementing the common superclass part sorted by priority
     */
    @SuppressWarnings("unchecked")
    @Override
    public Collection<E> getValues() {
        return (Collection<E>) globalContext.getPriorizedParts(type);
    }
}
