/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.process.logs;

import sirius.kernel.commons.Strings;
import sirius.kernel.nls.NLS;

public class ProcessLogAction {

    private ProcessLog logEntry;
    private String icon;
    private String action;
    private String label;

    public ProcessLogAction(ProcessLog logEntry, String action) {
        this.logEntry = logEntry;
        this.action = action;
        this.label = action;
        this.icon = "fa-chevron-right";
    }

    public ProcessLogAction withLabelKey(String labelKey) {
        this.label = NLS.get(labelKey);
        return this;
    }

    public ProcessLogAction withLabel(String label) {
        this.label = label;
        return this;
    }

    public ProcessLogAction withIcon(String icon) {
        this.icon = icon;
        return this;
    }

    public String getIcon() {
        return icon;
    }

    public String getAction() {
        return action;
    }

    public String getUri() {
        return Strings.apply("/ps/%s/action/%s/%s", logEntry.getProcess().getId(), logEntry.getId(), action);
    }

    public String getLabel() {
        return label;
    }
}
