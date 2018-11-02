/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.process;

import sirius.db.mixing.Nested;
import sirius.db.mixing.annotations.NullAllowed;

public class ProcessLink extends Nested {

    private String label;

    private String uri;

    @NullAllowed
    private String description;

    @NullAllowed
    private String type;
}
