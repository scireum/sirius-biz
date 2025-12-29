/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.analytics.reports;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import sirius.kernel.commons.Json;
import sirius.kernel.di.std.Register;

import javax.annotation.Nonnull;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Provides a cell format which renders the content as a list of lines.
 */
@Register(classes = {LinesCellFormat.class, CellFormat.class})
public class LinesCellFormat implements CellFormat {

    protected static final String TYPE = "lines";
    protected static final String KEY_VALUES = "values";

    @Override
    public String format(ObjectNode data) {
        return format(data, "<br>");
    }

    @Override
    public String rawValue(ObjectNode data) {
        return format(data, ", ");
    }

    private String format(ObjectNode data, String delimiter) {
        return Json.tryGetArray(data, KEY_VALUES)
                   .map(ArrayNode::valueStream)
                   .orElseGet(Stream::empty)
                   .map(JsonNode::asText)
                   .collect(Collectors.joining(delimiter));
    }

    @Nonnull
    @Override
    public String getName() {
        return TYPE;
    }
}
