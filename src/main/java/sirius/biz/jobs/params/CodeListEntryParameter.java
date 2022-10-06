/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.jobs.params;

import com.alibaba.fastjson.JSONObject;
import sirius.biz.codelists.CodeListEntry;
import sirius.biz.codelists.CodeLists;
import sirius.kernel.commons.Value;
import sirius.kernel.di.std.Part;
import sirius.kernel.nls.NLS;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Permits to select a {@link CodeListEntry} as parameter.
 */
public class CodeListEntryParameter extends ParameterBuilder<CodeListEntry<?, ?>, CodeListEntryParameter> {

    @Part
    @Nullable
    private static CodeLists<?, ?, ?> codeLists;

    private final String codelist;

    /**
     * Creates a new parameter with the given name and label.
     *
     * @param name     the name of the parameter
     * @param label    the label of the parameter, which will be {@link NLS#smartGet(String) auto translated}
     * @param codeList the name of the codelist to retrieve values from
     */
    public CodeListEntryParameter(String name, String label, String codeList) {
        super(name, label);
        this.codelist = codeList;
    }

    /**
     * Enumerates all entries provided by the codelist.
     *
     * @return the list of entries defined by the codelist
     */
    @SuppressWarnings("unchecked")
    public List<CodeListEntry<?, ?>> getValues() {
        return (List<CodeListEntry<?, ?>>) codeLists.getEntries(codelist);
    }

    /**
     * Returns the name of the template used to render the parameter in the UI.
     *
     * @return the name or path of the template used to render the parameter
     */
    @Override
    public String getTemplateName() {
        return "/templates/biz/jobs/params/codelistentry.html.pasta";
    }

    @Override
    protected String checkAndTransformValue(@Nonnull Value input) {
        if (!codeLists.hasValue(codelist, input.asString())) {
            return null;
        }
        return input.asString();
    }

    @Override
    public Optional<?> computeValueUpdate(Map<String, String> parameterContext) {
        return updater.apply(parameterContext)
                      .map(value -> new JSONObject().fluentPut("value", value.getCodeListEntryData().getCode())
                                                    .fluentPut("text",
                                                               value.getCodeListEntryData()
                                                                    .getTranslatedValue(NLS.getCurrentLang())));
    }

    @SuppressWarnings("unchecked")
    @Override
    protected Optional<CodeListEntry<?, ?>> resolveFromString(@Nonnull Value input) {
        if (input.isEmptyString()) {
            return Optional.empty();
        }
        return (Optional<CodeListEntry<?, ?>>) codeLists.getEntry(codelist, input.asString());
    }
}
