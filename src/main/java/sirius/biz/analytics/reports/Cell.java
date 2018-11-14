/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.analytics.reports;

import com.alibaba.fastjson.JSONObject;
import sirius.kernel.commons.Strings;
import sirius.kernel.di.std.Part;

public class Cell {

    private Object value;

    @Part
    private static Cells cells;

    public Cell(String value) {
        this.value = value;
    }

    public Cell(JSONObject value) {
        this.value = value;
    }

    public boolean isFilled() {
        return Strings.isFilled(value);
    }

    public String serializeToString() {
        return value instanceof JSONObject ? ((JSONObject) value).toJSONString() : (String) value;
    }

    public String render() {
        if (value instanceof JSONObject) {
            return cells.renderJSON((JSONObject) value);
        } else {
            return cells.render(serializeToString());
        }
    }
}
