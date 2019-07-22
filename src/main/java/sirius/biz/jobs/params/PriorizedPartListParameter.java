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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;

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
    @Override
    public Collection<E> getValues() {
        if (parts == null) {
            ArrayList<E> sortedParts = new ArrayList<>(globalContext.getParts(type));
            sortedParts.sort((o1, o2) -> {
                if (o1 == o2) {
                    return 0;
                }
                if (o2 == null) {
                    return -1;
                }
                if (o1 == null) {
                    return 1;
                }
                return o1.getPriority() - o2.getPriority();
            });

            parts = sortedParts;
        }
        return Collections.unmodifiableCollection(parts);
    }
}
