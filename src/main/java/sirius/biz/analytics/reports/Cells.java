/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.analytics.reports;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import sirius.kernel.commons.Amount;
import sirius.kernel.commons.NumberFormat;
import sirius.kernel.commons.Strings;
import sirius.kernel.di.GlobalContext;
import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.Register;
import sirius.kernel.health.Exceptions;
import sirius.kernel.nls.NLS;
import sirius.web.templates.ContentHelper;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Register(classes = Cells.class)
public class Cells {

    public static final String KEY_TYPE = "type";
    @Part
    private GlobalContext context;

    public Cell of(Object value) {
        if (Strings.isEmpty(value) || value instanceof Amount && ((Amount) value).isEmpty()) {
            return new Cell("");
        }
        if (value instanceof String) {
            return new Cell((String) value);
        }

        if (value instanceof JSONObject) {
            return new Cell((JSONObject) value);
        }

        return rightAligned(value);
    }

    public Cell rightAligned(Object value) {
        if (Strings.isEmpty(value)) {
            return of(value);
        }

        return new Cell(new JSONObject().fluentPut(KEY_TYPE, CSSCellFormat.TYPE)
                                        .fluentPut(CSSCellFormat.KEY_CLASSES, "align-right")
                                        .fluentPut(CSSCellFormat.KEY_VALUE, NLS.toUserString(value)));
    }

    public Cell colored(Amount value, @Nullable NumberFormat formatter) {
        if (value.isEmpty()) {
            return of(value);
        }

        String color = computeCellColor(value);

        return new Cell(new JSONObject().fluentPut(KEY_TYPE, CSSCellFormat.TYPE)
                                        .fluentPut(CSSCellFormat.KEY_CLASSES, "align-right " + color)
                                        .fluentPut(CSSCellFormat.KEY_VALUE, safeFormat(value, formatter)));
    }

    private String computeCellColor(Amount value) {
        String color = "";
        if (value.isPositive()) {
            color = "color green bold";
        } else if (value.isNegative()) {
            color = "color red bold";
        }
        return color;
    }

    private String safeFormat(Amount value, @Nullable NumberFormat formatter) {
        return value.toString(null != formatter ? formatter : NumberFormat.NO_DECIMAL_PLACES).asString();
    }

    public Cell trend(Amount value, Amount comparisonValue, @Nullable NumberFormat formatter) {
        Amount delta = value.percentageDifferenceOf(comparisonValue);
        if (delta.isEmpty()) {
            return of("");
        }

        String color = computeCellColor(delta);

        return new Cell(new JSONObject().fluentPut(KEY_TYPE, TrendCellFormat.TYPE)
                                        .fluentPut(TrendCellFormat.KEY_CLASSES, color)
                                        .fluentPut(TrendCellFormat.KEY_HINT,
                                                   safeFormat(value, formatter) + " / " + safeFormat(comparisonValue,
                                                                                                     formatter))
                                        .fluentPut(TrendCellFormat.KEY_TREND, delta.toString(NumberFormat.PERCENT)));
    }

    public Cell valueAndTrend(Amount value, Amount comparisonValue, @Nullable NumberFormat formatter) {
        Amount delta = value.percentageDifferenceOf(comparisonValue);
        if (delta.isEmpty()) {
            return of(safeFormat(value, formatter));
        }

        String color = computeCellColor(delta);

        return new Cell(new JSONObject().fluentPut(KEY_TYPE, TrendCellFormat.TYPE)
                                        .fluentPut(TrendCellFormat.KEY_CLASSES, color)
                                        .fluentPut(TrendCellFormat.KEY_HINT,
                                                   safeFormat(value, formatter) + " / " + safeFormat(comparisonValue,
                                                                                                     formatter))
                                        .fluentPut(TrendCellFormat.KEY_VALUE, safeFormat(value, formatter))
                                        .fluentPut(TrendCellFormat.KEY_TREND, delta.toString(NumberFormat.PERCENT)));
    }

    public Cell valueAndTrendIcon(Amount value, Amount comparisonValue, @Nullable NumberFormat formatter) {
        Amount delta = value.percentageDifferenceOf(comparisonValue);
        if (delta.isEmpty()) {
            return of(safeFormat(value, formatter));
        }

        String icon = "fa-arrow-right";
        if (delta.isPositive()) {
            icon = "fa-arrow-up";
        } else if (delta.isNegative()) {
            icon = "fa-arrow-down";
        }

        String color = computeCellColor(delta);

        return new Cell(new JSONObject().fluentPut(KEY_TYPE, TrendCellFormat.TYPE)
                                        .fluentPut(TrendCellFormat.KEY_HINT, delta.toString(NumberFormat.PERCENT))
                                        .fluentPut(TrendCellFormat.KEY_VALUE, safeFormat(value, formatter))
                                        .fluentPut(TrendCellFormat.KEY_CLASSES, color)
                                        .fluentPut(TrendCellFormat.KEY_ICON, icon));
    }

    public Cell link(Object value, String url) {
        if (Strings.isEmpty(value) || Strings.isEmpty(url)) {
            return of(value);
        }

        return new Cell(new JSONObject().fluentPut(KEY_TYPE, LinkCellFormat.TYPE)
                                        .fluentPut(LinkCellFormat.KEY_URL, url)
                                        .fluentPut(LinkCellFormat.KEY_VALUE, NLS.toUserString(value)));
    }

    public Cell sparkline(Amount value, NumberFormat formatter, List<Amount> sparkline) {
        if (value.isEmpty()) {
            return of(value);
        }

        return new Cell(new JSONObject().fluentPut(KEY_TYPE, SparklineCellFormat.TYPE)
                                        .fluentPut(SparklineCellFormat.KEY_VALUES,
                                                   sparkline.stream()
                                                            .map(v -> v.isEmpty() ?
                                                                      "0" :
                                                                      String.valueOf(v.getAmount().doubleValue()))
                                                            .collect(Collectors.joining(",")))
                                        .fluentPut(SparklineCellFormat.KEY_VALUE, safeFormat(value, formatter)));
    }

    public String render(String column, Map<String, String> row) {
        return render(row.get(column));
    }

    public String render(String cellValue) {
        if (Strings.isEmpty(cellValue)) {
            return "";
        }

        if (cellValue.startsWith("{")) {
            try {
                JSONObject data = JSON.parseObject(cellValue);
                return renderJSON(data);
            } catch (Exception e) {
                Exceptions.ignore(e);
            }
        }

        return ContentHelper.escapeXML(cellValue);
    }

    protected String renderJSON(JSONObject data) {
        TableCellFormat cell = context.getPart(data.getString(KEY_TYPE), TableCellFormat.class);
        if (cell != null) {
            return cell.format(data);
        } else {
            return "";
        }
    }
}
