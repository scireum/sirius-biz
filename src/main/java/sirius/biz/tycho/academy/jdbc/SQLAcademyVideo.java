/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.tycho.academy.jdbc;

import sirius.biz.tycho.academy.AcademyVideo;
import sirius.biz.tycho.academy.AcademyVideoData;
import sirius.db.jdbc.SQLEntity;

public class SQLAcademyVideo extends SQLEntity implements AcademyVideo {

    private final AcademyVideoData academyVideoData = new AcademyVideoData();

    @Override
    public AcademyVideoData getAcademyVideoData() {
        return academyVideoData;
    }
}
