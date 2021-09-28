/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.analytics.reports;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONException;
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

/**
 * Creates and renders {@link Cell cells} to be used in tables or metrics views.
 */
@Register(classes = Cells.class)
public class Cells {

    /**
     * The JSON key which is used to determine which {@link CellFormat} should be used to render a JSON representation
     * of a cell.
     */
    public static final String KEY_TYPE = "type";

    @Part
    private GlobalContext context;

    /**
     * Creates a cell for a given value.
     * <p>
     * Strings and empty values will be returned as plain cells. Everything else will be made a
     * {@link #rightAligned(Object) right aligned} cell.
     *
     * @param value the value to wrap as cell
     * @return the cell representing the wrapped value
     */
    public Cell of(Object value) {
        if (Strings.isEmpty(value) || value instanceof Amount && ((Amount) value).isEmpty()) {
            return new Cell("");
        }
        if (value instanceof String) {
            return new Cell((String) value);
        }
        if (value instanceof Enum) {
            return new Cell(Strings.toString((value)));
        }

        if (value instanceof JSONObject) {
            return new Cell((JSONObject) value);
        }

        return rightAligned(value);
    }

    /**
     * Creates a right aligned cell.
     *
     * @param value the value to render
     * @return a right-aligned cell containing the given value
     */
    public Cell rightAligned(Object value) {
        if (Strings.isEmpty(value)) {
            return of(value);
        }

        return new Cell(new JSONObject().fluentPut(KEY_TYPE, CSSCellFormat.TYPE)
                                        .fluentPut(CSSCellFormat.KEY_CLASSES, "text-right")
                                        .fluentPut(CSSCellFormat.KEY_VALUE, NLS.toUserString(value)));
    }

    /**
     * Creates a bold cell value.
     *
     * @param value the value to render
     * @return a cell containing the given value printed in a bold font face
     */
    public Cell bold(Object value) {
        if (Strings.isEmpty(value)) {
            return of(value);
        }

        return new Cell(new JSONObject().fluentPut(KEY_TYPE, CSSCellFormat.TYPE)
                                        .fluentPut(CSSCellFormat.KEY_CLASSES, "text-bold")
                                        .fluentPut(CSSCellFormat.KEY_VALUE, NLS.toUserString(value)));
    }

    /**
     * Creates a cell which contains a list of bullet points.
     *
     * @param values the values to render
     * @return a cell containing the given values
     */
    public Cell list(List<String> values) {
        return new Cell(new JSONObject().fluentPut(KEY_TYPE, ListCellFormat.TYPE)
                                        .fluentPut(ListCellFormat.KEY_VALUES, values));
    }

    /**
     * Generates a green cell.
     *
     * @param value the value to output
     * @return a cell which is colored green
     */
    public Cell green(Object value) {
        return new Cell(new JSONObject().fluentPut(KEY_TYPE, CSSCellFormat.TYPE)
                                        .fluentPut(CSSCellFormat.KEY_CLASSES, "text-sirius-green")
                                        .fluentPut(CSSCellFormat.KEY_VALUE, NLS.toUserString(value)));
    }

    /**
     * Generates a yellow cell.
     *
     * @param value the value to output
     * @return a cell which is colored red
     */
    public Cell yellow(Object value) {
        return new Cell(new JSONObject().fluentPut(KEY_TYPE, CSSCellFormat.TYPE)
                                        .fluentPut(CSSCellFormat.KEY_CLASSES, "text-sirius-yellow")
                                        .fluentPut(CSSCellFormat.KEY_VALUE, NLS.toUserString(value)));
    }

    /**
     * Generates a red cell.
     *
     * @param value the value to output
     * @return a cell which is colored red
     */
    public Cell red(Object value) {
        return new Cell(new JSONObject().fluentPut(KEY_TYPE, CSSCellFormat.TYPE)
                                        .fluentPut(CSSCellFormat.KEY_CLASSES, "text-sirius-red")
                                        .fluentPut(CSSCellFormat.KEY_VALUE, NLS.toUserString(value)));
    }

    /**
     * Generates a colored cell.
     *
     * @param value     the value to output
     * @param formatter the number format to use
     * @return a cell which is green for positive values and red for negative ones
     */
    public Cell colored(Amount value, @Nullable NumberFormat formatter) {
        if (value.isEmpty()) {
            return of(value);
        }

        String color = computeCellColor(value);

        return new Cell(new JSONObject().fluentPut(KEY_TYPE, CSSCellFormat.TYPE)
                                        .fluentPut(CSSCellFormat.KEY_CLASSES, "text-right " + color)
                                        .fluentPut(CSSCellFormat.KEY_VALUE, safeFormat(value, formatter)));
    }

    private String computeCellColor(Amount value) {
        String color = "";
        if (value.isPositive()) {
            color = "text-sirius-green text-bold";
        } else if (value.isNegative()) {
            color = "text-sirius-red text-bold";
        }
        return color;
    }

    private String safeFormat(Amount value, @Nullable NumberFormat formatter) {
        return value.toString(null != formatter ? formatter : NumberFormat.NO_DECIMAL_PLACES).asString();
    }

    /**
     * Computes the difference (in percent) and outputs it as colored cell.
     *
     * @param value           the base value
     * @param comparisonValue the value to compare to
     * @param formatter       the formatter used to format the given values (in the tooltip)
     * @return a cell containing the difference in percent
     */
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
                                        .fluentPut(TrendCellFormat.KEY_TREND, delta.toString(NumberFormat.PERCENT).get()));
    }

    /**
     * Outputs the given value as well as the difference in percent to the given <tt>comparisonValue</tt>.
     *
     * @param value           the base value which is also output
     * @param comparisonValue the value to compare to
     * @param formatter       the formatter used to format the given values
     * @return a cell containing the value and the difference in percent
     */
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
                                        .fluentPut(TrendCellFormat.KEY_TREND, delta.toString(NumberFormat.PERCENT).get()));
    }

    /**
     * Outputs the given value as well as the difference to the given <tt>comparisonValue</tt> as indicator icon.
     *
     * @param value           the base value which is also output
     * @param comparisonValue the value to compare to
     * @param formatter       the formatter used to format the given values
     * @return a cell containing the value and the difference indicated as icon (up or down)
     */
    public Cell valueAndTrendIcon(Amount value, Amount comparisonValue, @Nullable NumberFormat formatter) {
        Amount delta = value.percentageDifferenceOf(comparisonValue);
        if (delta.isEmpty()) {
            return of(safeFormat(value, formatter));
        }

        String icon = "fa fa-arrow-right";
        if (delta.isPositive()) {
            icon = "fa fa-arrow-up";
        } else if (delta.isNegative()) {
            icon = "fa fa-arrow-down";
        }

        String color = computeCellColor(delta);

        return new Cell(new JSONObject().fluentPut(KEY_TYPE, TrendCellFormat.TYPE)
                                        .fluentPut(TrendCellFormat.KEY_HINT, delta.toString(NumberFormat.PERCENT))
                                        .fluentPut(TrendCellFormat.KEY_VALUE, safeFormat(value, formatter))
                                        .fluentPut(TrendCellFormat.KEY_CLASSES, color)
                                        .fluentPut(TrendCellFormat.KEY_ICON, icon));
    }

    /**
     * Creates a link with the given label and url.
     *
     * @param value the value / label to show
     * @param url   the url of the link
     * @return a cell rendered as link
     */
    public Cell link(Object value, String url) {
        if (Strings.isEmpty(value) || Strings.isEmpty(url)) {
            return of(value);
        }

        return new Cell(new JSONObject().fluentPut(KEY_TYPE, LinkCellFormat.TYPE)
                                        .fluentPut(LinkCellFormat.KEY_URL, url)
                                        .fluentPut(LinkCellFormat.KEY_VALUE, NLS.toUserString(value)));
    }

    /**
     * Create a cell containing the given value and a sparkline to visualize the long-term trend of it.
     *
     * @param value     the value to display
     * @param formatter the formatter to format the value
     * @param sparkline the data representing the sparkline to render
     * @return a cell rendered as value along with a sparkline behind it
     */
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

    /**
     * Renders the given column of the given row.
     *
     * @param column the column to render
     * @param row    the row the read the cell from
     * @return an HTML string representing the requested cell
     */
    public String render(String column, Map<String, String> row) {
        return render(row.get(column));
    }

    /**
     * Renders the given cell.
     *
     * @param cellValue either a plain value or a JSON string generated by {@link Cell#serializeToString()}.
     * @return an HTML string representing the given cell
     */
    public String render(String cellValue) {
        if (Strings.isEmpty(cellValue)) {
            return "";
        }

        if (JSON.isValidObject(cellValue)) {
            try {
                JSONObject data = JSON.parseObject(cellValue);
                return renderJSON(data);
            } catch (JSONException e) {
                Exceptions.ignore(e);
            }
        }

        return ContentHelper.escapeXML(cellValue);
    }

    /**
     * Returns the raw value of a given cell value.
     *
     * @param cellValue either a plain value or a JSON string generated by {@link Cell#serializeToString()}.
     * @return a raw String value representing the given cell
     */
    public String rawValue(String cellValue) {
        if (Strings.isEmpty(cellValue)) {
            return "";
        }

        if (JSON.isValidObject(cellValue)) {
            try {
                JSONObject data = JSON.parseObject(cellValue);
                return renderRaw(data);
            } catch (JSONException e) {
                Exceptions.ignore(e);
            }
        }

        return cellValue;
    }

    protected String renderJSON(JSONObject data) {
        CellFormat cell = context.getPart(data.getString(KEY_TYPE), CellFormat.class);
        if (cell != null) {
            return cell.format(data);
        } else {
            return "";
        }
    }

    protected String renderRaw(JSONObject data) {
        CellFormat cell = context.getPart(data.getString(KEY_TYPE), CellFormat.class);
        if (cell != null) {
            return cell.rawValue(data);
        } else {
            return "";
        }
    }
}
