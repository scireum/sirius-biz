/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.tycho.academy;

import sirius.kernel.commons.Hasher;
import sirius.kernel.commons.URLBuilder;
import sirius.kernel.di.std.Register;
import sirius.kernel.settings.Extension;
import sirius.kernel.xml.StructuredNode;
import sirius.kernel.xml.XMLCall;

import javax.annotation.Nonnull;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Provides {@link AcademyVideoData academy videos} by loading them from OXOMI.
 */
@Register
public class OXOMIAcademyProvider implements AcademyProvider {

    private static final String ACADEMY_SERVICE_URI = "/service/xml/academy/videos";
    private static final String PARAM_PORTAL = "portal";
    private static final String PARAM_USER = "user";
    private static final String PARAM_ACADEMY_ID = "academyId";
    private static final String PARAM_HOST = "host";

    private static final String RESPONSE_ENTRIES = "list/entry";
    private static final String RESPONSE_ID = "id";
    private static final String RESPONSE_CODE = "code";
    private static final String RESPONSE_TITLE = "title";
    private static final String RESPONSE_DESCRIPTION = "description";
    private static final String RESPONSE_MANDATORY = "mandatory";
    private static final String RESPONSE_PRIORITY = "priority";
    private static final String RESPONSE_DURATION = "duration";
    private static final String RESPONSE_REQUIRED_ROLE = "requiredRole";
    private static final String RESPONSE_REQUIRED_FEATURE = "requiredFeature";
    private static final String RESPONSE_TRACK_ID = "trackId";
    private static final String RESPONSE_TRACK = "track";
    private static final String RESPONSE_TRACK_DESCRIPTION = "trackDescription";
    private static final String RESPONSE_PREVIEW_IMAGE = "previewImage";

    private static final String OXOMI_DEFAULT_HOST = "oxomi.com";
    private static final String PROTOCOL_HTTPS = "https://";
    private static final String PARAM_ACCESS_TOKEN = "accessToken";

    @Override
    public void fetchVideos(String academy, Extension config, Consumer<AcademyVideoData> videoConsumer)
            throws Exception {
        URLBuilder urlBuilder = new URLBuilder(PROTOCOL_HTTPS + getHostName(config) + ACADEMY_SERVICE_URI);
        urlBuilder.addParameter(PARAM_PORTAL, getPortalId(config))
                  .addParameter(PARAM_USER, getUserName(config))
                  .addParameter(PARAM_ACCESS_TOKEN, computeAccessToken(config))
                  .addParameter(PARAM_ACADEMY_ID, config.getString(PARAM_ACADEMY_ID));
        XMLCall call = XMLCall.to(urlBuilder.asURI());
        for (StructuredNode node : call.getInput().getNode(".").queryNodeList(RESPONSE_ENTRIES)) {
            AcademyVideoData video = new AcademyVideoData();
            video.setVideoId(node.queryString(RESPONSE_ID));
            video.setVideoCode(node.queryValue(RESPONSE_CODE)
                                   .replaceEmptyWith(node.queryString(RESPONSE_ID))
                                   .asString());
            video.setTitle(node.queryString(RESPONSE_TITLE));
            video.setDescription(node.queryString(RESPONSE_DESCRIPTION));
            video.setMandatory(node.queryValue(RESPONSE_MANDATORY).asBoolean());
            video.setPriority(node.queryValue(RESPONSE_PRIORITY).asInt(100));
            video.setDuration(node.queryValue(RESPONSE_DURATION).asInt(0));
            video.setRequiredPermission(node.queryString(RESPONSE_REQUIRED_ROLE));
            video.setRequiredFeature(node.queryString(RESPONSE_REQUIRED_FEATURE));
            video.setTrackId(node.queryString(RESPONSE_TRACK_ID));
            video.setTrackName(node.queryString(RESPONSE_TRACK));
            video.setTrackDescription(node.queryString(RESPONSE_TRACK_DESCRIPTION));
            video.setPreviewUrl(node.queryString(RESPONSE_PREVIEW_IMAGE));
            videoConsumer.accept(video);
        }
    }

    @Override
    public String getVideoTemplate() {
        return "/templates/biz/tycho/academy/oxomi-video.html.pasta";
    }

    /**
     * Extracts the OXOMI username to use.
     *
     * @param config the config of the provider
     * @return the username to use for the authentication against OXOMI
     */
    public String getUserName(Extension config) {
        return config.getString(PARAM_USER);
    }

    /**
     * Determines the portal id to use.
     *
     * @param config the config of the provider
     * @return the OXOMI portal to use
     */
    public String getPortalId(Extension config) {
        return config.getString(PARAM_PORTAL);
    }

    /**
     * Returns the hostname to use.
     * <p>
     * This is commonly <tt>oxomi.com</tt> but might be changed when accessing the test system.
     *
     * @param config the config of the provider
     * @return the hostname of the system to target
     */
    public String getHostName(Extension config) {
        return config.get(PARAM_HOST).asString(OXOMI_DEFAULT_HOST);
    }

    /**
     * Computes the OXOMI accessToken to perform an authentication.
     *
     * @param config the config of the provider
     * @return the accessToken used to access the OXOMI portal
     */
    public String computeAccessToken(Extension config) {
        return Hasher.md5()
                     .hash(config.getString("sharedSecret"))
                     .hash(Hasher.md5()
                                 .hash(config.getString("sharedSecret"))
                                 .hash(getPortalId(config))
                                 .hash(getUserName(config))
                                 .hash(String.valueOf(TimeUnit.DAYS.convert(System.currentTimeMillis(),
                                                                            TimeUnit.MILLISECONDS)))
                                 .toHexString())
                     .toHexString();
    }

    @Nonnull
    @Override
    public String getName() {
        return "oxomi";
    }
}
