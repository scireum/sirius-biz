/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.tycho.academy.mongo;

import sirius.biz.tycho.academy.AcademyVideo;
import sirius.biz.tycho.academy.AcademyVideoData;
import sirius.db.mixing.annotations.Index;
import sirius.db.mongo.Mango;
import sirius.db.mongo.MongoEntity;
import sirius.kernel.di.std.Framework;

/**
 * Stores an {@link AcademyVideoData academy video} in MongoDB.
 */
@Framework(MongoOnboardingEngine.FRAMEWORK_TYCHO_MONGO_ACADEMIES)
@Index(name = "academy_lookup",
        columns = {"academyVideoData_academy", "academyVideoData_syncToken"},
        columnSettings = {Mango.INDEX_ASCENDING, Mango.INDEX_ASCENDING})
public class MongoAcademyVideo extends MongoEntity implements AcademyVideo {

    private final AcademyVideoData academyVideoData = new AcademyVideoData();

    @Override
    public AcademyVideoData getAcademyVideoData() {
        return academyVideoData;
    }
}
