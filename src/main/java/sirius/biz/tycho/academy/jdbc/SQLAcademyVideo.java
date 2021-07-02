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
import sirius.db.mixing.annotations.Index;
import sirius.kernel.di.std.Framework;

/**
 * Stores an {@link AcademyVideoData academy video} in a JDBC/SQL database.
 */
@Framework(SQLOnboardingEngine.FRAMEWORK_TYCHO_JDBC_ACADEMIES)
@Index(name = "academy_lookup", columns = {"academyVideoData_academy", "academyVideoData_syncToken"})
public class SQLAcademyVideo extends SQLEntity implements AcademyVideo {

    private final AcademyVideoData academyVideoData = new AcademyVideoData();

    @Override
    public AcademyVideoData getAcademyVideoData() {
        return academyVideoData;
    }
}
