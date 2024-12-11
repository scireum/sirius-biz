/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.charts;

import org.apache.batik.anim.dom.SVGDOMImplementation;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import sirius.kernel.commons.Strings;

import javax.annotation.Nullable;
import java.awt.Dimension;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.SequencedCollection;
import java.util.SequencedSet;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * Represents a spider chart (radar chart) which can be rendered as SVG.
 *
 * @param <N> the type of the numeric values
 */
public class SpiderChart<N extends Number> extends Chart {

    private static final double TICK_LENGTH = 2.0;

    private final SequencedSet<String> labels;

    private final List<Dataset<N>> datasets = new ArrayList<>();

    private final List<N> marks = new ArrayList<>();

    private boolean rings = false;

    /**
     * Helper that allows to format the numeric values of the slices.
     */
    private Function<N, String> formatter = Object::toString;

    /**
     * Creates a new spider chart with the given axis labels.
     *
     * @param labels the labels for the chart axes
     */
    public SpiderChart(String... labels) {
        this(List.of(labels));
    }

    /**
     * Creates a new spider chart with the given axis labels.
     *
     * @param labels the labels for the chart axes
     */
    public SpiderChart(SequencedCollection<String> labels) {
        if (labels.size() < 3) {
            throw new IllegalArgumentException("At least three keys are required.");
        }

        this.labels = new LinkedHashSet<>(labels);
    }

    /**
     * Adds the given dataset to the chart.
     *
     * @param dataset the dataset to add
     * @return the chart itself for fluent method calls
     */
    public SpiderChart<N> addDataset(Dataset<N> dataset) {
        if (!labels.equals(dataset.getLabels())) {
            throw new IllegalArgumentException("Incompatible dataset labels.");
        }
        this.datasets.add(dataset);
        return this;
    }

    /**
     * Adds a dataset with the given values to the chart.
     *
     * @param values the values of the dataset
     * @return the chart itself for fluent method calls
     */
    public SpiderChart<N> addDataset(List<N> values) {
        return addDataset(null, values);
    }

    /**
     * Adds a dataset with the given values to the chart.
     *
     * @param name   the name of the dataset
     * @param values the values of the dataset
     * @return the chart itself for fluent method calls
     */
    public SpiderChart<N> addDataset(@Nullable String name, List<N> values) {
        if (values.size() != labels.size()) {
            throw new IllegalArgumentException("Incompatible dataset size.");
        }

        var dataset = new Dataset<N>().withName(name);
        AtomicInteger labelCounter = new AtomicInteger();
        labels.forEach(label -> {
            int labelIndex = labelCounter.getAndIncrement();
            dataset.addSlice(label, values.get(labelIndex));
        });

        return addDataset(dataset);
    }

    /**
     * Sets the marks for the chart that are rendered as small ticks on the axes.
     *
     * @param marks the marks to set
     * @return the chart itself for fluent method calls
     * @see #withRings()
     */
    public SpiderChart<N> withMarks(List<N> marks) {
        this.marks.clear();
        this.marks.addAll(marks);
        this.marks.sort(Comparator.comparingDouble(Number::doubleValue));
        return this;
    }

    /**
     * Enables drawing of rings around the chart, connecting the equivalent marks of all the axes.
     *
     * @return the chart itself for fluent method calls
     * @see #withMarks(List)
     */
    public SpiderChart<N> withRings() {
        this.rings = true;
        return this;
    }

    /**
     * Sets a formatter for the numeric values of the marks.
     *
     * @param formatter the formatter to use
     * @return the chart itself for fluent method calls
     */
    public SpiderChart<N> withFormatter(Function<N, String> formatter) {
        this.formatter = formatter;
        return this;
    }

    public SequencedSet<String> getLabels() {
        return Collections.unmodifiableSequencedSet(labels);
    }

    public List<Dataset<N>> getDatasets() {
        return Collections.unmodifiableList(datasets);
    }

    @Override
    public Element toSvg(Dimension bounds) {
        Element svgElement = Charts.createSvgElementWithCenteredViewbox(bounds);

        Element backgroundGroupElement =
                svgElement.getOwnerDocument().createElementNS(SVGDOMImplementation.SVG_NAMESPACE_URI, TAG_G);
        svgElement.appendChild(backgroundGroupElement);

        Element axesGroupElement =
                svgElement.getOwnerDocument().createElementNS(SVGDOMImplementation.SVG_NAMESPACE_URI, TAG_G);
        backgroundGroupElement.appendChild(axesGroupElement);

        Element labelsGroupElement =
                svgElement.getOwnerDocument().createElementNS(SVGDOMImplementation.SVG_NAMESPACE_URI, TAG_G);
        backgroundGroupElement.appendChild(labelsGroupElement);

        Element graphsGroupElement =
                svgElement.getOwnerDocument().createElementNS(SVGDOMImplementation.SVG_NAMESPACE_URI, TAG_G);
        svgElement.appendChild(graphsGroupElement);

        double radius = 0.5 * Math.min(bounds.width, bounds.height) - 7.5;
        double normaliser = computeNormaliser();

        // draw scale with circles and labels
        drawAxes(svgElement.getOwnerDocument(), radius, normaliser, axesGroupElement, labelsGroupElement);

        AtomicInteger datasetCounter = new AtomicInteger();
        datasets.forEach(dataset -> {
            int datasetIndex = datasetCounter.getAndIncrement();
            StringBuilder pathDefinition = new StringBuilder();

            dataset.withColor(Dataset.COLORS.get(datasetIndex % Dataset.COLORS.size()));

            AtomicInteger labelCounter = new AtomicInteger();
            labels.forEach(label -> {
                int labelIndex = labelCounter.getAndIncrement();

                double radians = Math.TAU * labelIndex / labels.size();
                double sine = Math.sin(radians);
                double cosine = Math.cos(radians);

                double value = dataset.resolveSlice(label)
                                      .map(Dataset.Slice::getQuantity)
                                      .map(Number::doubleValue)
                                      .orElse(0.0);
                double normalizedValue = radius * value / normaliser;

                pathDefinition.append(pathDefinition.isEmpty() ? "M" : "L")
                              .append(' ')
                              .append(sine * normalizedValue)
                              .append(' ')
                              .append(-cosine * normalizedValue);
            });

            Element valuePath =
                    svgElement.getOwnerDocument().createElementNS(SVGDOMImplementation.SVG_NAMESPACE_URI, TAG_PATH);
            valuePath.setAttribute(ATTRIBUTE_D, pathDefinition.append(" Z").toString());
            valuePath.setAttribute(ATTRIBUTE_STROKE, dataset.getColor());
            valuePath.setAttribute(ATTRIBUTE_FILL, dataset.getColor());
            valuePath.setAttribute(ATTRIBUTE_FILL_OPACITY, "0.5");

            // graphs are drawn in reverse order to ensure that the first dataset is on top
            if (graphsGroupElement.hasChildNodes()) {
                graphsGroupElement.insertBefore(valuePath, graphsGroupElement.getFirstChild());
            } else {
                graphsGroupElement.appendChild(valuePath);
            }
        });

        return svgElement;
    }

    /**
     * Computes the normaliser for the chart, which is used to scale the values to the chart size. The value is either
     * the largest mark or the largest value in the datasets.
     *
     * @return the normaliser to scale the chart
     */
    private double computeNormaliser() {
        Stream<N> numbers = marks.isEmpty() ?
                            datasets.stream().flatMap(dataset -> dataset.stream().map(Dataset.Slice::getQuantity)) :
                            marks.stream();
        return numbers.mapToDouble(Number::doubleValue).max().orElse(1.0);
    }

    private void drawAxes(Document document,
                          double radius,
                          double normaliser,
                          Element axesGroupElement,
                          Element labelsGroupElement) {

        // first, draw the rings (if enabled) and numeric/value labels along the upright axis
        marks.forEach(mark -> {
            double markRadius = radius * mark.doubleValue() / normaliser;

            if (rings) {
                Element circleElement = document.createElementNS(SVGDOMImplementation.SVG_NAMESPACE_URI, TAG_CIRCLE);
                circleElement.setAttribute(ATTRIBUTE_CX, "0");
                circleElement.setAttribute(ATTRIBUTE_CY, "0");
                circleElement.setAttribute(ATTRIBUTE_R, Double.toString(markRadius));
                circleElement.setAttribute(ATTRIBUTE_STROKE, COLOR_LIGHT_GRAY);
                circleElement.setAttribute(ATTRIBUTE_FILL, VALUE_FILL_NONE);
                circleElement.setAttribute(ATTRIBUTE_STROKE_WIDTH, "0.1");
                axesGroupElement.appendChild(circleElement);
            }

            Element markTextElement = document.createElementNS(SVGDOMImplementation.SVG_NAMESPACE_URI, TAG_TEXT);
            markTextElement.setTextContent(formatter.apply(mark));
            markTextElement.setAttribute(ATTRIBUTE_X, Double.toString(1 + 0.5 * TICK_LENGTH));
            markTextElement.setAttribute(ATTRIBUTE_Y, Double.toString(-markRadius + 0.75));
            markTextElement.setAttribute(ATTRIBUTE_FONT_SIZE, Double.toString(3.0));
            markTextElement.setAttribute(ATTRIBUTE_FILL, COLOR_BLACK);
            labelsGroupElement.appendChild(markTextElement);
        });

        // then, draw the actual axes and descriptive labels
        AtomicInteger labelCounter = new AtomicInteger();
        labels.forEach(label -> {
            int labelIndex = labelCounter.getAndIncrement();

            double radians = Math.TAU * labelIndex / labels.size();
            double sine = Math.sin(radians);
            double cosine = Math.cos(radians);

            // draw axis
            double axisRadius = radius + 2.5;
            Element axisPath = document.createElementNS(SVGDOMImplementation.SVG_NAMESPACE_URI, TAG_PATH);
            axisPath.setAttribute(ATTRIBUTE_D, String.format("M 0 0 L %f %f", sine * axisRadius, -cosine * axisRadius));
            axisPath.setAttribute(ATTRIBUTE_STROKE, COLOR_BLACK);
            axisPath.setAttribute(ATTRIBUTE_STROKE_WIDTH, "0.2");
            axesGroupElement.appendChild(axisPath);

            // draw marks/ticks along the axis
            marks.forEach(mark -> {
                double markRadius = radius * mark.doubleValue() / normaliser;
                double markX = sine * markRadius;
                double markY = -cosine * markRadius;

                Element markPath = document.createElementNS(SVGDOMImplementation.SVG_NAMESPACE_URI, TAG_PATH);
                markPath.setAttribute(ATTRIBUTE_D,
                                      String.format("M %f %f L %f %f",
                                                    markX + 0.5 * TICK_LENGTH * cosine,
                                                    markY + 0.5 * TICK_LENGTH * sine,
                                                    markX - 0.5 * TICK_LENGTH * cosine,
                                                    markY - 0.5 * TICK_LENGTH * sine));
                markPath.setAttribute(ATTRIBUTE_STROKE, COLOR_BLACK);
                markPath.setAttribute(ATTRIBUTE_STROKE_WIDTH, "0.2");
                axesGroupElement.appendChild(markPath);
            });

            // draw label at the end of the axis
            double labelRadius = radius + 3.5;
            labelsGroupElement.appendChild(createTextElementForAxisLabel(document,
                                                                         sine * labelRadius,
                                                                         -cosine * labelRadius,
                                                                         label,
                                                                         COLOR_BLACK));
        });
    }

    private Element createTextElementForAxisLabel(Document document, double x, double y, String label, String color) {
        String textAnchor;
        if (x < -0.1) {
            textAnchor = VALUE_TEXT_ANCHOR_END;
        } else if (x > 0.1) {
            textAnchor = VALUE_TEXT_ANCHOR_START;
        } else {
            textAnchor = VALUE_TEXT_ANCHOR_MIDDLE;
        }

        double yOffset;
        if (y < -0.1) {
            yOffset = 0.0;
        } else if (y > 0.1) {
            yOffset = 2.5;
        } else {
            yOffset = 0.75;
        }

        Element labelElement = document.createElementNS(SVGDOMImplementation.SVG_NAMESPACE_URI, TAG_TEXT);
        labelElement.setTextContent(label);

        labelElement.setAttribute(ATTRIBUTE_X, Double.toString(x));
        labelElement.setAttribute(ATTRIBUTE_Y, Double.toString(y + yOffset));
        labelElement.setAttribute(ATTRIBUTE_FONT_SIZE, Double.toString(3.0));

        if (Strings.isFilled(color)) {
            labelElement.setAttribute(ATTRIBUTE_FILL, color);
        }

        // compute text anchor
        labelElement.setAttribute(ATTRIBUTE_TEXT_ANCHOR, textAnchor);

        return labelElement;
    }
}
