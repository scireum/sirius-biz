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

import java.awt.Dimension;

/**
 * Provides an interface for charts that can be rendered as SVG.
 */
public abstract class Chart {

    /**
     * Contains the tag name for circle elements, {@value}.
     */
    protected static final String TAG_CIRCLE = "circle";

    /**
     * Contains the tag name for group elements, {@value}.
     */
    protected static final String TAG_GROUP = "g";

    /**
     * Contains the tag name for path elements, {@value}.
     */
    protected static final String TAG_PATH = "path";

    /**
     * Contains the tag name for SVG root elements, {@value}.
     */
    protected static final String TAG_SVG = "svg";

    /**
     * Contains the tag name for text elements, {@value}.
     */
    protected static final String TAG_TEXT = "text";

    /**
     * Contains the attribute name for the center x coordinate, {@value}.
     */
    protected static final String ATTRIBUTE_CENTER_X = "cx";

    /**
     * Contains the attribute name for the center y coordinate, {@value}.
     */
    protected static final String ATTRIBUTE_CENTER_Y = "cy";

    /**
     * Contains the attribute name for path definitions, {@value}.
     */
    protected static final String ATTRIBUTE_DEFINITION = "d";

    /**
     * Contains the attribute name for the fill colour, {@value}.
     */
    protected static final String ATTRIBUTE_FILL = "fill";

    /**
     * Contains the attribute name for the fill opacity, {@value}.
     */
    protected static final String ATTRIBUTE_FILL_OPACITY = "fill-opacity";

    /**
     * Contains the attribute name for the font size, {@value}.
     */
    protected static final String ATTRIBUTE_FONT_SIZE = "font-size";

    /**
     * Contains the attribute name for the radius, {@value}.
     */
    protected static final String ATTRIBUTE_RADIUS = "r";

    /**
     * Contains the attribute name for the stroke colour, {@value}.
     */
    protected static final String ATTRIBUTE_STROKE = "stroke";

    /**
     * Contains the attribute name for the stroke width, {@value}.
     */
    protected static final String ATTRIBUTE_STROKE_WIDTH = "stroke-width";

    /**
     * Contains the attribute name for the text anchor, {@value}.
     */
    protected static final String ATTRIBUTE_TEXT_ANCHOR = "text-anchor";

    /**
     * Contains the attribute name for the view box, {@value}.
     */
    protected static final String ATTRIBUTE_VIEW_BOX = "viewBox";

    /**
     * Contains the attribute name for the x coordinate, {@value}.
     */
    protected static final String ATTRIBUTE_X = "x";

    /**
     * Contains the attribute name for the y coordinate, {@value}.
     */
    protected static final String ATTRIBUTE_Y = "y";

    /**
     * Contains the value for the {@linkplain #ATTRIBUTE_FILL fill attribute} to avoid filling, {@value}.
     */
    protected static final String VALUE_FILL_NONE = "none";

    /**
     * Contains the value for the {@linkplain #ATTRIBUTE_TEXT_ANCHOR text anchor attribute} to align text at the start,
     * {@value}.
     */
    protected static final String VALUE_TEXT_ANCHOR_START = "start";

    /**
     * Contains the value for the {@linkplain #ATTRIBUTE_TEXT_ANCHOR text anchor attribute} to align text centered,
     * {@value}.
     */
    protected static final String VALUE_TEXT_ANCHOR_MIDDLE = "middle";

    /**
     * Contains the value for the {@linkplain #ATTRIBUTE_TEXT_ANCHOR text anchor attribute} to align text at the end,
     * {@value}.
     */
    protected static final String VALUE_TEXT_ANCHOR_END = "end";

    /**
     * Contains the black colour as hex-string, {@value}. The value is used as primary colour for charts.
     */
    protected static final String COLOR_BLACK = "#000000";

    /**
     * Contains a dark gray colour as hex-string, {@value}. Its design system equivalent is {@code sirius-gray-dark}.
     * <p>
     * The value is used as secondary colour for charts.
     */
    protected static final String COLOR_GRAY_DARK = "#808080";

    /**
     * Contains the light gray colour as hex-string, {@value}. Its design system equivalent is {@code sirius-gray-light}.
     * <p>
     * The value is used as secondary colour for charts.
     */
    protected static final String COLOR_GRAY_LIGHT = "#c7c7c7";

    /**
     * Renders the chart as SVG.
     *
     * @param bounds the dimensions of the viewport
     * @return the SVG representation of the chart
     */
    public abstract Element toSvg(Dimension bounds);

    /**
     * Creates an empty SVG element with the view box centered.
     *
     * @param bounds the dimensions of the viewport
     * @return an empty SVG element with the view box centered
     */
    protected Element createSvgElementWithCenteredViewbox(Dimension bounds) {
        Element svgElement = SVGDOMImplementation.getDOMImplementation()
                                                 .createDocument(SVGDOMImplementation.SVG_NAMESPACE_URI,
                                                                 Chart.TAG_SVG,
                                                                 null)
                                                 .getDocumentElement();
        svgElement.setAttribute(Chart.ATTRIBUTE_VIEW_BOX,
                                String.format("%f %f %f %f",
                                              -0.5 * bounds.width,
                                              -0.5 * bounds.height,
                                              (double) bounds.width,
                                              (double) bounds.height));
        return svgElement;
    }
}
