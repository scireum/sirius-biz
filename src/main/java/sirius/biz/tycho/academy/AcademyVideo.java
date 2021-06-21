/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.tycho.academy;

import sirius.db.mixing.Mapping;

/**
 * Represents a video which is part of a video academy managed by the {@link OnboardingEngine}.
 * <p>
 * Note that an <tt>AcademyVideo</tt> provides the metadata of a video to show and gets multiplied into many
 * {@link OnboardingVideo onboarding videos} - one for each participant.
 */
public interface AcademyVideo {

    /**
     * Contains the database independent metadata of the video.
     */
    Mapping ACADEMY_VIDEO_DATA = Mapping.named("academyVideoData");

    AcademyVideoData getAcademyVideoData();
}
