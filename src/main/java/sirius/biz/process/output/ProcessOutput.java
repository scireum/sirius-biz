/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.process.output;

import sirius.db.mixing.Nested;
import sirius.db.mixing.types.StringMap;
import sirius.kernel.di.GlobalContext;
import sirius.kernel.di.std.Part;
import sirius.kernel.nls.NLS;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class ProcessOutput extends Nested {

    private String name;

    private String label;

    private String type;

    private final StringMap context = new StringMap();

    @Part
    private static GlobalContext globalContext;

    private static Map<String, String> typeToIcons = new ConcurrentHashMap<>();

    public ProcessOutput withName(String name) {
        this.name = name;
        return this;
    }

    public String getName() {
        return name;
    }

    public String getLabel() {
        return NLS.smartGet(label);
    }

    public ProcessOutput withLabel(String label) {
        this.label = label;
        return this;
    }

    public String getType() {
        return type;
    }

    public ProcessOutput withType(String type) {
        this.type = type;
        return this;
    }

    public StringMap getContext() {
        return context;
    }

    public String getIcon() {
        return typeToIcons.computeIfAbsent(type, this::computeIcon);
    }

    private String computeIcon(String outputType) {
        return Optional.of(globalContext.getPart(outputType, ProcessOutputType.class))
                       .map(ProcessOutputType::getIcon)
                       .orElse("fa-bars");
    }
}
