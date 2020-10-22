/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.tycho.academy;

import sirius.db.mixing.Mapping;

public interface AcademyVideo {

    Mapping ACADEMY_VIDEO_DATA = Mapping.named("academyVideoData");
    AcademyVideoData getAcademyVideoData();
}
