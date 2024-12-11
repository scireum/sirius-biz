/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.charts;

import com.lowagie.text.xml.XmlDomWriter;
import org.w3c.dom.Element;
import sirius.kernel.di.std.Register;
import sirius.web.templates.pdf.TagliatellePDFContentHandler;

import java.awt.Dimension;
import java.io.StringWriter;

/**
 * Provides helpers to work with charts.
 */
@Register(classes = Charts.class)
public class Charts {

    /**
     * Exports a chart as SVG string that is compatible with {@linkplain TagliatellePDFContentHandler PDF rendering}.
     *
     * @param chart  the chart to export
     * @param bounds the dimensions of the viewport
     * @return the SVG string representing the chart
     */
    public String exportChartForPdf(BaseChart chart, Dimension bounds) {
        // we need to clean the SVG code a bit to make it compatible with the PDF renderer
        Element element = chart.toSvg(bounds);
        element.setAttribute("style",
                             String.format("display: block; width: 100%%; height: %dmm; page-break-inside: avoid;",
                                           bounds.height));

        // note that the string writer uses a string buffer internally; no additional buffering or flushing is required
        StringWriter out = new StringWriter();
        XmlDomWriter xmlOut = new XmlDomWriter();
        xmlOut.setOutput(out);
        xmlOut.write(element);
        return out.toString();
    }
}
