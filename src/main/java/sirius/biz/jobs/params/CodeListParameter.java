/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.jobs.params;

import sirius.biz.codelists.CodeList;
import sirius.biz.codelists.CodeLists;
import sirius.db.mixing.BaseEntity;
import sirius.kernel.commons.Value;
import sirius.kernel.di.std.Part;
import sirius.kernel.nls.NLS;

import java.util.Optional;

/**
 * Permits to select a {@link CodeList} as parameter.
 *
 * @param <I> the type of the ID used by subclasses.
 * @param <V> the type of selectable by this parameter.
 */
public class CodeListParameter<I, V extends BaseEntity<I> & CodeList> extends Parameter<V, CodeListParameter<I, V>> {

    public static final String CODE_LISTS_AUTOCOMPLETE = "/code-lists/autocomplete";

    @Part
    private static CodeLists<?, ?, ?> codeLists;

    /**
     * Creates a new parameter with the given name and label.
     *
     * @param name  the name of the parameter
     * @param label the label of the parameter, which will be {@link NLS#smartGet(String) auto translated}
     */
    public CodeListParameter(String name, String label) {
        super(name, label);
    }

    /**
     * Returns the name of the template used to render the parameter in the UI.
     *
     * @return the name or path of the template used to render the parameter
     */
    @Override
    public String getTemplateName() {
        return "/templates/biz/jobs/params/codelist-autocomplete.html.pasta";
    }

    @Override
    protected String checkAndTransformValue(Value input) {
        return resolveFromString(input).map(codeList -> codeList.getCodeListData().getCode()).orElse(null);
    }

    @Override
    @SuppressWarnings("unchecked")
    protected Optional<V> resolveFromString(Value input) {
        if (input.isEmptyString()) {
            return Optional.empty();
        }

        return (Optional<V>) codeLists.findCodelist(input.getString());
    }
}
