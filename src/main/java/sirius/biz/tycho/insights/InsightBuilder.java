/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.tycho.insights;

import sirius.kernel.commons.Context;
import sirius.kernel.commons.Strings;

import javax.annotation.CheckReturnValue;
import java.util.HashMap;
import java.util.Map;

public class InsightBuilder {

    private String targetObject;
    private String errorKind;
    private final Map<String, String> context = new HashMap<>();

    @CheckReturnValue
    public InsightBuilder withTargetObject(String targetObject) {
        this.targetObject = targetObject;
        return this;
    }

    @CheckReturnValue
    public InsightBuilder withErrorKind(String errorKind) {
        this.errorKind = errorKind;
        return this;
    }

    @CheckReturnValue
    public InsightBuilder withContext(Map<String, String> contextData) {
        if (contextData != null) {
            contextData.forEach(this::withContext);
        }

        return this;
    }

    @CheckReturnValue
    public InsightBuilder withContext(String key, String value) {
        if (Strings.isFilled(key) && Strings.isFilled(value)) {
            this.context.put(key, value);
        }

        return this;
    }

}
