/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.jobs.params;

import com.alibaba.fastjson.JSONObject;
import sirius.biz.codelists.CodeList;
import sirius.biz.codelists.CodeLists;
import sirius.kernel.commons.Value;
import sirius.kernel.di.std.Part;
import sirius.kernel.nls.NLS;

import javax.annotation.Nullable;
import java.util.Map;
import java.util.Optional;

/**
 * Permits to select a {@link CodeList} as parameter.
 */
public class CodeListParameter extends ParameterBuilder<CodeList, CodeListParameter> {

    @Part
    @Nullable
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
    public Optional<?> computeValueUpdate(Map<String, String> parameterContext) {
        return updater.apply(parameterContext)
                      .map(value -> new JSONObject().fluentPut("value", value.getCodeListData().getCode())
                                                    .fluentPut("text", value.getCodeListData().getCode()));
    }

    @Override
    @SuppressWarnings("unchecked")
    protected Optional<CodeList> resolveFromString(Value input) {
        if (input.isEmptyString()) {
            return Optional.empty();
        }

        return (Optional<CodeList>) codeLists.findCodelist(input.getString());
    }
}
