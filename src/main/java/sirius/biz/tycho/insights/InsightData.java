/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.tycho.insights;

import sirius.db.mixing.Composite;
import sirius.db.mixing.annotations.Length;
import sirius.db.mixing.annotations.NullAllowed;

public class InsightData extends Composite {

    @Length(255)
    private String handler;

    @Length(50)
    private String tenantId;

    @NullAllowed
    @Length(50)
    private String userId;

    @NullAllowed
    @Length(150)
    private String requiredPermission;

    @NullAllowed
    @Length(100)
    private String targetObject;

    @NullAllowed
    @Length(100)
    private String errorKind;

}
