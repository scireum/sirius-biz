/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.params;

import sirius.kernel.commons.Strings;
import sirius.kernel.commons.Value;
import sirius.kernel.health.Exceptions;
import sirius.kernel.nls.NLS;

public abstract class Parameter<P extends Parameter> {

    protected String name;
    protected String title;
    protected String description;
    protected boolean required;
    protected int span = 6;
    protected int smallSpan = 12;

    public Parameter(String name, String title) {
        this.name = name;
        this.title = title;
    }

    @SuppressWarnings("unchecked")
    protected P self() {
        return (P) this;
    }

    public P withDescription(String description) {
        this.description = description;
        return self();
    }

    public P withSpan(int span, int smallSpan) {
        this.span = span;
        this.smallSpan = smallSpan;

        return self();
    }

    public P markRequired() {
        this.required = true;
        return self();
    }

    public String getName() {
        return name;
    }

    public String getTitle() {
        return title.startsWith("$") ? NLS.get(title.substring(1)) : title;
    }

    public String getDescription() {
        if (description == null) {
            return "";
        }
        return description.startsWith("$") ? NLS.get(description.substring(1)) : description;
    }

    public abstract String getTemplateName();

    public Object checkAndTransform(Value input) {
        try {
            Object result = checkAndTransformValue(input);
            if (Strings.isEmpty(result) && required) {
                throw Exceptions.createHandled().withNLSKey("Parameter.required").set("name", getTitle()).handle();
            }
            return null;
        } catch (IllegalArgumentException e) {
            throw Exceptions.createHandled()
                            .withNLSKey("Parameter.invalidValue")
                            .set("name", getTitle())
                            .set("message", e.getMessage())
                            .handle();
        }
    }

    protected abstract Object checkAndTransformValue(Value input) throws IllegalArgumentException;

    public int getSpan() {
        return span;
    }

    public int getSmallSpan() {
        return smallSpan;
    }

    public boolean isRequired() {
        return required;
    }
}
