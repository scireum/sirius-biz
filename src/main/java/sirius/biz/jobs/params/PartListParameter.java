/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.jobs.params;

import sirius.kernel.commons.Value;
import sirius.kernel.di.GlobalContext;
import sirius.kernel.di.std.Part;
import sirius.kernel.nls.NLS;

import java.util.Collection;
import java.util.Collections;
import java.util.Optional;

/**
 * Provides the selection of a {@link Part} from a list of parts with a common {@link sirius.kernel.di.std.Register registered} superclass as parameter.
 *
 * @param <E> the common {@link sirius.kernel.di.std.Register registered} superclass
 */
public class PartListParameter<E> extends ParameterBuilder<E, PartListParameter<E>> {

    @Part
    protected static GlobalContext globalContext;

    protected final Class<E> type;

    protected Collection<E> parts;

    /**
     * Creates a new parameter with the given name and label.
     *
     * @param name  the name of the parameter
     * @param label the label of the parameter, which will be {@link NLS#smartGet(String) auto translated}
     * @param type  the type of parts being fetched
     */
    public PartListParameter(String name, String label, Class<E> type) {
        super(name, label);
        this.type = type;
    }

    @Override
    public String getTemplateName() {
        return "/templates/biz/jobs/params/part-list.html.pasta";
    }

    /**
     * Enumerates all parts implementing the common superclass part.
     *
     * @return the list of parts implementing the common superclass part
     */
    public Collection<E> getValues() {
        if (parts == null) {
            parts = globalContext.getParts(type);
        }
        return Collections.unmodifiableCollection(parts);
    }

    @Override
    protected String checkAndTransformValue(Value input) {
        if (input.isEmptyString()) {
            return null;
        }

        String partName = input.getString();

        if (getValues().stream().noneMatch(p -> p.getClass().getName().equals(partName))) {
            return null;
        }

        return partName;
    }

    @Override
    protected Optional<E> resolveFromString(Value input) {
        if (input.isEmptyString()) {
            return Optional.empty();
        }
        String partName = input.getString();
        return getValues().stream().filter(p -> p.getClass().getName().equals(partName)).findFirst();
    }
}
