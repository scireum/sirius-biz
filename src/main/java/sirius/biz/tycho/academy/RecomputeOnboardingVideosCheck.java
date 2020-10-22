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
import sirius.kernel.di.std.Part;

import java.util.Set;
import java.util.function.Consumer;

public abstract class RecomputeOnboardingVideosCheck<E extends BaseEntity<?> & OnboardingParticipant>
        extends DailyCheck<E> {

    @Part
    private OnboardingEngine onboardingEngine;

    private class Computation {

        private String academy;
        private E entity;
        private Set<String> currentVideoIds;

        private int available = 0;
        private int watchable = 0;
        private int skipped = 0;
        private int watched = 0;
        private int mandatoryAvailable = 0;
        private int mandatoryHandled = 0;

        protected Computation(String academy, E entity) {
            this.academy = academy;
            this.entity = entity;
            this.currentVideoIds = onboardingEngine.fetchCurrentOnboardingVideoIds(academy, entity.getUniqueName());
        }

        protected boolean checkPermission(String permission) {
            return RecomputeOnboardingVideosCheck.this.checkPermission(entity, permission);
        }

        protected void perform() {
            onboardingEngine.fetchAcademyVideos(academy)
                            .stream()
                            .filter(video -> checkPermission(video.getAcademyVideoData().getRequiredPermission()))
                            .filter(video -> checkPermission(video.getAcademyVideoData().getRequiredFeature()))
                            .forEach(video -> {
                                OnboardingVideo onboardingVideo =
                                        onboardingEngine.createOrUpdateOnboardingVideo(entity.getUniqueName(), video);
                                currentVideoIds.remove(onboardingVideo.getIdAsString());
                                updateCounters(video, onboardingVideo.getOnboardingVideoData());
                            });

            onboardingEngine.markOutdatedOnboardingVideosAsDeleted(academy, entity.getUniqueName(), currentVideoIds);
            updateStatistics();
            persistStatistics();
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
            if (entity instanceof Traced) {
                ((Traced) entity).getTrace().setSilent(true);
            }
            entity.getDescriptor().getMapper().update(entity);
        }
    }

    @Override
    protected void execute(E entity) {
        if (onboardingEngine == null) {
            return;
        }

        determineAcademies(entity, academy -> new Computation(academy, entity).perform());
    }

    protected abstract boolean checkPermission(E entity, String permission);

    protected abstract void determineAcademies(E entity, Consumer<String> academyConsumer);
}


