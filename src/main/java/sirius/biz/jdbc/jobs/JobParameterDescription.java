/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.jdbc.jobs;

import sirius.kernel.di.GlobalContext;
import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.Priorized;

/**
 * Created by aha on 22.07.16.
 */
public class JobParameterDescription {
    private String name;
    private String title;
    private String type;
    private String defaultValue;
    private int priority = Priorized.DEFAULT_PRIORITY;
    private String description;
    private boolean required;

    @Part
    private static GlobalContext ctx;

    public String getName() {
        return name;
    }

    public String getTitle() {
        return title;
    }

    public String getType() {
        return type;
    }

    public String getDefaultValue() {
        return defaultValue;
    }

    public int getPriority() {
        return priority;
    }

    public String getDescription() {
        return description;
    }

    public boolean isRequired() {
        return required;
    }

    public ParameterHandler getParameterHandler() {
        return ctx.findPart(type, ParameterHandler.class);
    }
}
