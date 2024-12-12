/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.charts;

/**
 * Represents a doughnut chart which can be rendered as SVG.
 *
 * @param <N> the type of the numeric values
 */
public class DoughnutChart<N extends Number> extends PieChart<N> {

    /**
     * Creates an empty doughnut chart.
     */
    public DoughnutChart() {
        this.doughnut = true;
    }
}
