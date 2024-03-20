/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.tycho.academy;

import sirius.biz.analytics.checks.DailyCheck;
import sirius.biz.protocol.Traced;
import sirius.db.mixing.BaseEntity;
import sirius.kernel.commons.Strings;
import sirius.kernel.di.std.Part;
import sirius.kernel.health.Exceptions;
import sirius.kernel.health.Log;

import javax.annotation.Nullable;
import java.util.function.Consumer;

/**
 * Provides a base implementation of a {@link DailyCheck} which recomputes {@link OnboardingVideo onboarding videos}.
 * <p>
 * This is the actual work horse which collects all available videos for an academy as {@link AcademyVideo} and
 * then multiplies them out into {@link OnboardingVideo onboarding videos} - one for each entity which happens
 * to be an {@link OnboardingParticipant}.
 *
 * @param <E> the entity type which represents the onboarding participant
 */
public abstract class RecomputeOnboardingVideosCheck<E extends BaseEntity<?> & OnboardingParticipant>
        extends DailyCheck<E> {

    @Part
    @Nullable
    protected static OnboardingEngine onboardingEngine;

    @Override
    protected void execute(E entity) {
        if (onboardingEngine == null) {
            return;
        }

        determineAcademies(entity, academy -> new Computation(academy, entity).perform());
    }

    @Override
    public boolean isEnabled() {
        return onboardingEngine != null;
    }

    /**
     * Checks if the given entity fulfills the given permission string.
     *
     * @param entity     the entity to check the permissions for
     * @param permission the permissions to check
     * @return <tt>true</tt> if the requested permissions are available, <tt>false</tt> otherwise
     */
    protected abstract boolean checkPermission(E entity, String permission);

    /**
     * Determines which academies are relevant for the given entity.
     *
     * @param entity          the entity to check the academies for
     * @param academyConsumer the consumer to be supplied with the enabled academies
     */
    protected abstract void determineAcademies(E entity, Consumer<String> academyConsumer);

    /**
     * Wraps the whole process of creating onboarding videos for one participant to carry along some state.
     */
    private class Computation {

        private final String academy;
        private final E entity;
        private final String syncToken;

        private int available;
        private int watchable;
        private int skipped;
        private int watched;
        private int mandatoryAvailable;
        private int mandatoryHandled;

        protected Computation(String academy, E entity) {
            this.academy = academy;
            this.entity = entity;
            this.syncToken = Strings.generateCode(32);
        }

        protected void perform() {
            try {
                onboardingEngine.fetchAcademyVideos(academy)
                                .stream()
                                .filter(video -> checkPermission(entity,
                                                                 video.getAcademyVideoData().getRequiredPermission()))
                                .filter(video -> checkPermission(entity,
                                                                 video.getAcademyVideoData().getRequiredFeature()))
                                .forEach(video -> {
                                    OnboardingVideo onboardingVideo = onboardingEngine.createOrUpdateOnboardingVideo(
                                            entity.getUniqueName(),
                                            video,
                                            syncToken);
                                    updateCounters(video, onboardingVideo.getOnboardingVideoData());
                                });

                onboardingEngine.markOutdatedOnboardingVideosAsDeleted(academy, entity.getUniqueName(), syncToken);
                updateStatistics();
                persistStatistics();
            } catch (Exception exception) {
                Exceptions.handle(Log.BACKGROUND, exception);
            }
        }

        private void updateCounters(AcademyVideo video, OnboardingVideoData videoData) {
            available++;
            if (video.getAcademyVideoData().isMandatory()) {
                mandatoryAvailable++;
                if (videoData.isWatched() || videoData.isSkipped()) {
                    mandatoryHandled++;
                }
            }
            if (videoData.isWatched()) {
                watched++;
            } else if (videoData.isSkipped()) {
                skipped++;
            } else {
                watchable++;
            }
        }

        private void updateStatistics() {
            entity.getOnboardingData().setNumMandatoryAvailableVideos(available);
            entity.getOnboardingData().setNumAvailableVideos(available);
            entity.getOnboardingData().setNumWatchableVideos(watchable);

            if (available > 0) {
                entity.getOnboardingData().setPercentWatched(100 * watched / available);
                entity.getOnboardingData().setPercentSkipped(100 * skipped / available);
            } else {
                entity.getOnboardingData().setPercentWatched(0);
                entity.getOnboardingData().setPercentSkipped(0);
            }
            if (mandatoryAvailable > 0) {
                entity.getOnboardingData().setEducationLevelPercent(100 * mandatoryHandled / mandatoryAvailable);
            } else {
                entity.getOnboardingData().setEducationLevelPercent(0);
            }
        }

        protected void persistStatistics() {
            if (entity instanceof Traced traced) {
                traced.getTrace().setSilent(true);
            }
            entity.getDescriptor().getMapper().update(entity);
        }
    }
}
