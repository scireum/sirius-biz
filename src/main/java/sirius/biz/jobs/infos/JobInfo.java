/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.jobs.infos;

/**
 * Represents a block or piece of documentation for a {@link sirius.biz.jobs.JobFactory job}.
 */
public interface JobInfo {

    /**
     * Determines which template to use to render this block.
     *
     * @return the name / path of the template to use
     */
    String getTemplateName();
}
