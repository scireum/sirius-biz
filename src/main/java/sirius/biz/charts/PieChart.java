/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.charts;

import org.apache.batik.anim.dom.SVGDOMImplementation;
import org.w3c.dom.Element;
import sirius.kernel.commons.Strings;

import java.awt.Dimension;
import java.awt.geom.Point2D;
import java.util.concurrent.atomic.DoubleAdder;

/**
 * Represents a pie chart which can be rendered as SVG.
 *
 * @param <N> the type of the numeric values
 * @see DoughnutChart
 */
public class PieChart<N extends Number> extends Chart {

    protected static final double DOUGHNUT_WIDTH = 4.0;

    protected Dataset<N> dataset;

    /**
     * Flag to determine if the pie chart should be rendered as a doughnut rather than filled.
     */
    protected boolean doughnut = false;

    /**
     * Sets up the chart with the given dataset.
     *
     * @param dataset the dataset to render
     * @return the chart itself for fluent method calls
     */
    public PieChart<N> withDataset(Dataset<N> dataset) {
        this.dataset = dataset;
        return this;
    }

    public Dataset<N> getDataset() {
        return dataset;
    }

    @Override
    public Element toSvg(Dimension bounds) {
        Element svgElement = Charts.createSvgElementWithCenteredViewbox(bounds);

        Element pieGroupElement =
                svgElement.getOwnerDocument().createElementNS(SVGDOMImplementation.SVG_NAMESPACE_URI, TAG_GROUP);
        svgElement.appendChild(pieGroupElement);

        Element labelsGroupElement =
                svgElement.getOwnerDocument().createElementNS(SVGDOMImplementation.SVG_NAMESPACE_URI, TAG_GROUP);
        svgElement.appendChild(labelsGroupElement);

        double radius = 0.5 * Math.min(bounds.width, bounds.height) - 5.0;

        // determines the multipliers to represent each slice in degrees and as percent
        double multiplierRadians = Math.TAU / dataset.sum();

        DoubleAdder accumulatedRadians = new DoubleAdder();
        Point2D pin = new Point2D.Double();
        Point2D label = new Point2D.Double();
        Point2D previousLabel = new Point2D.Double();
        dataset.stream().forEach(slice -> {
            double radians = slice.doubleValue() * multiplierRadians;
            if (radians <= 0.0) {
                return;
            }

            double startRadians = accumulatedRadians.doubleValue();
            accumulatedRadians.add(radians);
            double endRadians = accumulatedRadians.doubleValue();

            pieGroupElement.appendChild(createPathElementForSlice(svgElement,
                                                                  startRadians,
                                                                  endRadians,
                                                                  radius,
                                                                  slice.getColor()));

            double halfRadians = 0.5 * (startRadians + endRadians);
            pin.setLocation(Math.sin(halfRadians) * (radius - 0.5 * DOUGHNUT_WIDTH),
                            -Math.cos(halfRadians) * (radius - 0.5 * DOUGHNUT_WIDTH));

            label.setLocation((pin.getX() > 0 ? 0.5 : -0.5) * bounds.width, pin.getY());
            String textAnchor = pin.getX() > 0 ? VALUE_TEXT_ANCHOR_END : VALUE_TEXT_ANCHOR_START;

            // skip overlapping labels, relying on an externally printed legend
            if (label.distanceSq(previousLabel) < 25.0) {
                return;
            }
            previousLabel.setLocation(label);

            Element labelGroupElement =
                    svgElement.getOwnerDocument().createElementNS(SVGDOMImplementation.SVG_NAMESPACE_URI, TAG_GROUP);
            labelsGroupElement.appendChild(labelGroupElement);

            Element labelCircle =
                    svgElement.getOwnerDocument().createElementNS(SVGDOMImplementation.SVG_NAMESPACE_URI, TAG_CIRCLE);
            labelCircle.setAttribute(ATTRIBUTE_CENTER_X, Double.toString(pin.getX()));
            labelCircle.setAttribute(ATTRIBUTE_CENTER_Y, Double.toString(pin.getY()));
            labelCircle.setAttribute(ATTRIBUTE_RADIUS, "0.4");
            labelCircle.setAttribute(ATTRIBUTE_FILL, COLOR_BLACK);
            labelGroupElement.appendChild(labelCircle);

            Element labelPath =
                    svgElement.getOwnerDocument().createElementNS(SVGDOMImplementation.SVG_NAMESPACE_URI, TAG_PATH);
            labelPath.setAttribute(ATTRIBUTE_DEFINITION,
                                   String.format("M %f %f L %f %f",
                                                 pin.getX(),
                                                 pin.getY(),
                                                 label.getX(),
                                                 label.getY()));
            labelPath.setAttribute(ATTRIBUTE_STROKE, COLOR_BLACK);
            labelPath.setAttribute(ATTRIBUTE_STROKE_WIDTH, "0.2");
            labelGroupElement.appendChild(labelPath);

            labelGroupElement.appendChild(createTextElementForLabel(svgElement,
                                                                    label.getX(),
                                                                    label.getY() - 1.5,
                                                                    3.0,
                                                                    COLOR_BLACK,
                                                                    textAnchor,
                                                                    slice.getLabel()));
            labelGroupElement.appendChild(createTextElementForLabel(svgElement,
                                                                    label.getX(),
                                                                    label.getY() + 3.5,
                                                                    3.0,
                                                                    COLOR_GRAY_DARK,
                                                                    textAnchor,
                                                                    slice.formatQuantity()));
        });

        return svgElement;
    }

    private Element createPathElementForSlice(Element svgElement,
                                              double startRadians,
                                              double endRadians,
                                              double radius,
                                              String color) {
        Element piecePath =
                svgElement.getOwnerDocument().createElementNS(SVGDOMImplementation.SVG_NAMESPACE_URI, TAG_PATH);

        piecePath.setAttribute(ATTRIBUTE_DEFINITION, assemblePathDefinitionForSlice(startRadians, endRadians, radius));
        piecePath.setAttribute(ATTRIBUTE_STROKE, COLOR_GRAY_DARK);
        piecePath.setAttribute(ATTRIBUTE_STROKE_WIDTH, "0.1");

        if (Strings.isFilled(color)) {
            piecePath.setAttribute(ATTRIBUTE_FILL, color);
        }

        return piecePath;
    }

    private String assemblePathDefinitionForSlice(double startRadians, double endRadians, double radius) {

        // if the start and end point are precisely the same, we need to add a small offset to avoid rendering issues
        double delta = endRadians - startRadians;
        double offset = (Math.abs(delta % Math.TAU) < 2.0e-4) ? 1.0e-4 : 0.0;

        // cache the sine and cosine values, as they are relatively expensive to compute
        double startSine = Math.sin(startRadians + offset);
        double startCosine = Math.cos(startRadians + offset);
        double endSine = Math.sin(endRadians - offset);
        double endCosine = Math.cos(endRadians - offset);

        double startX = startSine * radius;
        double startY = -startCosine * radius;

        double endX = endSine * radius;
        double endY = -endCosine * radius;

        double innerRadius = radius - DOUGHNUT_WIDTH;
        int largeArcFlag = delta > Math.PI ? 1 : 0;

        // draw a doughnut segment
        if (doughnut && innerRadius > 0) {
            double innerStartX = startSine * innerRadius;
            double innerStartY = -startCosine * innerRadius;

            double innerEndX = endSine * innerRadius;
            double innerEndY = -endCosine * innerRadius;

            return String.format("M %f %f A %f %f 0 %d 1 %f %f L %f %f A %f %f 0 %d 0 %f %f Z",
                                 startX,
                                 startY,
                                 radius,
                                 radius,
                                 largeArcFlag,
                                 endX,
                                 endY,
                                 innerEndX,
                                 innerEndY,
                                 innerRadius,
                                 innerRadius,
                                 largeArcFlag,
                                 innerStartX,
                                 innerStartY);
        }

        // draw a pie slice
        return String.format("M %f %f A %f %f 0 %d 1 %f %f L 0 0 Z",
                             startX,
                             startY,
                             radius,
                             radius,
                             largeArcFlag,
                             endX,
                             endY);
    }

    private Element createTextElementForLabel(Element svgElement,
                                              double x,
                                              double y,
                                              double fontSize,
                                              String fontColor,
                                              String textAnchor,
                                              String text) {
        Element valueLabel =
                svgElement.getOwnerDocument().createElementNS(SVGDOMImplementation.SVG_NAMESPACE_URI, TAG_TEXT);
        valueLabel.setTextContent(text);

        valueLabel.setAttribute(ATTRIBUTE_X, Double.toString(x));
        valueLabel.setAttribute(ATTRIBUTE_Y, Double.toString(y));
        valueLabel.setAttribute(ATTRIBUTE_FONT_SIZE, Double.toString(fontSize));

        if (Strings.isFilled(fontColor)) {
            valueLabel.setAttribute(ATTRIBUTE_FILL, fontColor);
        }

        if (Strings.isFilled(textAnchor)) {
            valueLabel.setAttribute(ATTRIBUTE_TEXT_ANCHOR, textAnchor);
        }

        return valueLabel;
    }
}
