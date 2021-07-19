/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.process.logs;

import sirius.biz.process.Process;
import sirius.kernel.commons.Strings;
import sirius.kernel.nls.NLS;
import sirius.web.http.WebContext;

import javax.annotation.Nonnull;

/**
 * Represents an action available for a {@link ProcessLog}.
 */
public class ProcessLogAction {

    private final ProcessLog logEntry;
    private String icon;
    private final String action;
    private String label;

    /**
     * Creates a new action for the given entry and name.
     *
     * @param logEntry the entry for which this action was created
     * @param action   the name of the action (Passed to
     *                 {@link ProcessLogHandler#executeAction(WebContext, Process, ProcessLog, String, String)} once
     *                 this action is invoked).
     */
    public ProcessLogAction(ProcessLog logEntry, String action) {
        this.logEntry = logEntry;
        this.action = action;
        this.label = action;
        this.icon = "fa-chevron-right";
    }

    /**
     * Specifies the label as NLS key.
     *
     * @param labelKey the NLS key which determines the label to use
     * @return the action itself for fluent method calls
     */
    public ProcessLogAction withLabelKey(String labelKey) {
        this.label = NLS.get(labelKey);
        return this;
    }

    /**
     * Specifies the label for this action.
     *
     * @param label the label to use
     * @return the action itself for fluent method calls
     */
    public ProcessLogAction withLabel(String label) {
        this.label = label;
        return this;
    }

    /**
     * Specifies the icon to use for this action.
     *
     * @param icon the icon to use
     * @return the action itself for fluent method calls
     */
    public ProcessLogAction withIcon(@Nonnull String icon) {
        this.icon = icon;
        return this;
    }

    /**
     * Generates the uri which represents this action.
     * <p>
     * This will invoke {@link sirius.biz.process.ProcessController#executeLogAction(WebContext, String, String, String)}
     * which then will invoke {@link ProcessLogHandler#executeAction(WebContext, Process, ProcessLog, String, String)}
     * with the given name as action.
     *
     * @return the uri representing this action
     */
    public String getUri() {
        return Strings.apply("/ps/%s/action/%s/%s", logEntry.getProcess().getId(), logEntry.getId(), action);
    }

    public String getIcon() {
        return icon;
    }

    public String getAction() {
        return action;
    }

    public String getLabel() {
        return label;
    }
}
