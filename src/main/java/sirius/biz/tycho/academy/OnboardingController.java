/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.tycho.academy;

import io.netty.handler.codec.http.HttpResponseStatus;
import sirius.biz.web.BizController;
import sirius.kernel.commons.Tuple;
import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.Register;
import sirius.kernel.settings.Extension;
import sirius.web.controller.Routed;
import sirius.web.http.WebContext;
import sirius.web.services.InternalService;
import sirius.web.services.JSONStructuredOutput;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Provides the UI for the onboarding / video academy framework.
 */
@Register
public class OnboardingController extends BizController {

    private static final String PARAM_SEEN_IN_PERCENT = "seenInPercent";
    private static final String PARAM_STARTED = "started";
    private static final String PARAM_SKIP = "skip";

    @Part
    @Nullable
    private OnboardingEngine onboardingEngine;

    /**
     * Renders an overview of the available track of the video academy for the given owner.
     *
     * @param webContext  the request to respond to
     * @param target      the target entity
     * @param accessToken the security token to authenticate the target
     */
    @Routed(value = "/academy/:1/:2", priority = 999)
    public void academy(WebContext webContext, String target, String accessToken) {
        if (!verifyURISignature(webContext, target, accessToken)) {
            return;
        }

        assertNotNull(onboardingEngine);

        webContext.respondWith()
                  .template("/templates/biz/tycho/academy/tracks.html.pasta",
                            onboardingEngine.fetchTracks(target),
                            target,
                            computeURISignature(target),
                            onboardingEngine.fetchSomeVideo(target).orElse(null));
    }

    /**
     * Lists all videos fo the given track.
     *
     * @param webContext  the request to respond to
     * @param target      the target entity
     * @param accessToken the security token to authenticate the target
     * @param track       the track to list the videos for
     */
    @Routed(value = "/academy/:1/:2/track/:3", priority = 999)
    public void track(WebContext webContext, String target, String accessToken, String track) {
        if (!verifyURISignature(webContext, target, accessToken)) {
            return;
        }

        assertNotNull(onboardingEngine);

        AcademyTrackInfo trackInfo = onboardingEngine.fetchTrack(target, track).orElse(null);
        assertNotNull(trackInfo);

        webContext.respondWith()
                  .template("/templates/biz/tycho/academy/track.html.pasta",
                            onboardingEngine.fetchVideos(target, track),
                            trackInfo,
                            target,
                            computeURISignature(target));
    }

    /**
     * Displays the requested video
     *
     * @param webContext  the request to respond to
     * @param target      the target entity
     * @param accessToken the security token to authenticate the target
     * @param videoId     the id of the video to show
     */
    @Routed(value = "/academy/:1/:2/video/:3", priority = 999)
    public void video(WebContext webContext, String target, String accessToken, String videoId) {
        if (!verifyURISignature(webContext, target, accessToken)) {
            return;
        }

        assertNotNull(onboardingEngine);

        OnboardingVideo video = onboardingEngine.fetchVideo(target, videoId);
        assertNotNull(video);

        List<? extends OnboardingVideo> otherRecommendations =
                onboardingEngine.fetchOtherRecommendations(target, videoId, video.fetchAcademyVideoData().getTrackId());

        Tuple<Extension, AcademyProvider> settings =
                onboardingEngine.fetchAcademySettings(video.fetchAcademyVideoData().getAcademy());

        webContext.respondWith()
                  .template("/templates/biz/tycho/academy/video.html.pasta",
                            video,
                            otherRecommendations,
                            target,
                            accessToken,
                            settings.getFirst(),
                            settings.getSecond());
    }

    /**
     * Provides a JSON API to update the statistics which record which video has been viewed or skipped.
     *
     * @param webContext  the request to respond to
     * @param out         the JSON response
     * @param target      the target entity
     * @param accessToken the security token to authenticate the target
     * @param videoId     the id of the onboarding video to update
     */
    @Routed(value = "/academy/:1/:2/update/:3", priority = 999)
    @InternalService
    public void updateVideo(WebContext webContext,
                            JSONStructuredOutput out,
                            String target,
                            String accessToken,
                            String videoId) {
        if (!verifyURISignature(webContext, target, accessToken)) {
            return;
        }
        assertNotNull(onboardingEngine);

        if (webContext.hasParameter(PARAM_SEEN_IN_PERCENT)) {
            onboardingEngine.updateWatchedPercent(target, videoId, webContext.require(PARAM_SEEN_IN_PERCENT).asInt(0));
        } else if (webContext.get(PARAM_STARTED).asBoolean()) {
            onboardingEngine.recordVideoStarted(target, videoId);
        } else if (webContext.get(PARAM_SKIP).asBoolean()) {
            onboardingEngine.markAsSkipped(target, videoId);
        }
    }
}
