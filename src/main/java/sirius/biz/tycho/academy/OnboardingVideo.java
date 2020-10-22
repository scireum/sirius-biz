/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.tycho.academy;

import sirius.db.mixing.Mapping;
import sirius.db.mixing.types.BaseEntityRef;

public interface OnboardingVideo {

    Mapping ACADEMY_VIDEO = Mapping.named("academyVideo");
    BaseEntityRef<?, ? extends AcademyVideo> getAcademyVideo();

    AcademyVideoData fetchAcademyVideoData();

    String getIdAsString();

    Mapping ONBOARDING_VIDEO_DATA = Mapping.named("onboardingVideoData");

     OnboardingVideoData getOnboardingVideoData();

}
