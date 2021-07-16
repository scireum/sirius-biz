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

/**
 * Describes an onboarding video which is derived from an {@link AcademyVideo}
 * (one for each {@link OnboardingParticipant}).
 */
public interface OnboardingVideo {

    /**
     * Contains the reference to the academy video.
     */
    Mapping ACADEMY_VIDEO = Mapping.named("academyVideo");

    BaseEntityRef<?, ? extends AcademyVideo> getAcademyVideo();

    /**
     * Fetches the actual academy video metadata.
     *
     * @return the academy video data
     */
    AcademyVideoData fetchAcademyVideoData();

    String getIdAsString();

    /**
     * Contains the participant specific data for this video.
     */
    Mapping ONBOARDING_VIDEO_DATA = Mapping.named("onboardingVideoData");

    OnboardingVideoData getOnboardingVideoData();
}
