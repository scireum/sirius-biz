/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.tycho.academy;

import sirius.biz.web.BizController;
import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.Register;
import sirius.web.controller.Controller;
import sirius.web.controller.Routed;
import sirius.web.http.WebContext;
import sirius.web.services.JSONStructuredOutput;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Register
public class OnboardingController extends BizController {

    private static final String RESPONSE_FOUND = "found";
    private static final String PRESONSE_ID = "id";
    private static final String RESPONSE_TITLE = "title";
    private static final String RESPONSE_DESCRIPTION = "description";
    private static final String RESPONSE_DURATION = "duration";
    private static final String RESPONSE_PREVIEW_URL = "previewUrl";

    private static final String PARAM_SEEN_IN_PERCENT = "seenInPercent";
    private static final String PARAM_STARTED = "started";
    private static final String PARAM_SKIP = "skip";

    @Part
    private OnboardingEngine onboardingEngine;

    @Routed("/academy/:1/:2")
    public void academy(WebContext webContext, String target, String accessToken) {
        verifyAccessToken(target, accessToken);

        List<AcademyTrackInfo> allTracks =
                onboardingEngine != null ? onboardingEngine.fetchTracks(target) : Collections.emptyList();
        List<AcademyTrackInfo> recommendedTracks = allTracks.stream()
                                                            .filter(track -> track.getNumberOfRecommendedVideos() > 0)
                                                            .collect(Collectors.toList());
        List<AcademyTrackInfo> seenTracks = allTracks.stream()
                                                     .filter(track -> track.getNumberOfRecommendedVideos() == 0)
                                                     .collect(Collectors.toList());

        webContext.respondWith()
                  .template("/templates/biz/tycho/academy/tracks.html.pasta",
                            recommendedTracks,
                            seenTracks,
                            target,
                            accessToken);
    }

    private void verifyAccessToken(String target, String accessToken) {
        //TODO implement!!
    }

    @Routed("/academy/:1/:2/track/:3")
    public void track(WebContext webContext, String target, String accessToken, String track) {
        verifyAccessToken(target, accessToken);
        List<? extends OnboardingVideo> videos =
                onboardingEngine != null ? onboardingEngine.fetchVideos(target, track) : Collections.emptyList();

        webContext.respondWith().template("/templates/biz/tycho/academy/track.html.pasta", videos, target, accessToken);
    }

    @Routed("/academy/:1/:2/video/:3")
    public void video(WebContext webContext, String target, String accessToken, String videoId) {
        verifyAccessToken(target, accessToken);
        OnboardingVideo video = onboardingEngine != null ? onboardingEngine.fetchVideo(target, videoId) : null;
        assertNotNull(video);

        List<? extends OnboardingVideo> otherRecommendations =
                onboardingEngine.fetchOtherRecommendations(target, videoId);
        webContext.respondWith()
                  .template("/templates/biz/tycho/academy/video.html.pasta",
                            video,
                            otherRecommendations,
                            target,
                            accessToken);
    }

    @Routed(value = "/academy/:1/:2/recommend", jsonCall = true)
    public void recommendVideo(WebContext webContext,JSONStructuredOutput out,
                            String target,
                            String accessToken) {
        if (onboardingEngine == null) {
            out.property(RESPONSE_FOUND, false);
            return;
        }

        Optional<? extends OnboardingVideo> recommendedVideo = onboardingEngine.fetchSomeVideo(target);
        if (!recommendedVideo.isPresent()) {
            out.property(RESPONSE_FOUND, false);
            return;
        }

        onboardingEngine.recordVideoShown(target,recommendedVideo.get().getIdAsString());
        AcademyVideoData videoData = recommendedVideo.get().fetchAcademyVideoData();
        out.property(RESPONSE_FOUND, true);
        out.property(PRESONSE_ID, videoData.getVideoId());
        out.property(RESPONSE_TITLE, videoData.getTitle());
        out.property(RESPONSE_DESCRIPTION, videoData.getDescription());
        out.property(RESPONSE_DURATION, videoData.getDurationAsString());
        out.property(RESPONSE_PREVIEW_URL, videoData.getPreviewUrl());
    }


    @Routed(value = "/academy/:1/:2/update/:3", jsonCall = true)
    public void updateVideo(WebContext webContext,
                            JSONStructuredOutput out,
                            String target,
                            String accessToken,
                            String videoId) {
        verifyAccessToken(target, accessToken);
        if (onboardingEngine == null) {
            return;
        }

        if (webContext.hasParameter(PARAM_SEEN_IN_PERCENT)) {
            onboardingEngine.updateWachedPercent(target, videoId, webContext.require(PARAM_SEEN_IN_PERCENT).asInt(0));
        } else if (webContext.get(PARAM_STARTED).asBoolean()) {
            onboardingEngine.recordVideoStarted(target, videoId);
        } else if (webContext.get(PARAM_SKIP).asBoolean()) {
            onboardingEngine.markAsSkipped(target, videoId);
        }
    }
}
