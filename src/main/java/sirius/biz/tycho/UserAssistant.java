/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.tycho;

import com.typesafe.config.ConfigException;
import sirius.kernel.Sirius;
import sirius.kernel.di.std.Register;
import sirius.web.http.WebContext;

import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Provides a helper to fetch the {@link sirius.biz.tycho.academy.OnboardingEngine academy} track or video or
 * the {@link sirius.biz.tycho.kb.KnowledgeBase kba} to link in the <tt>pageHeader</tt> of a Tycho page.
 * <p>
 * These values can either be provided via the system configuration. The maps <tt>user-assistant.academy-track</tt>,
 * <tt>user-assistant.academy-video</tt> and <tt>user-assistant.kba</tt> can be supplied with a path as key and
 * the respective code or id as value. The path is taken from the {@link WebContext#getRequestedURI() requested URI}.
 * <p>
 * If a controller needs to determine  the codes, the attributes (i.e. {@link #WEB_CONTEXT_SETTING_KBA} can be used
 * (by storing a value via {@link WebContext#setAttribute(String, Object)}).
 */
@Register(classes = UserAssistant.class)
public class UserAssistant {

    /**
     * Contains the name of the attribute used to specify the video track to link to.
     *
     * @see WebContext#setAttribute(String, Object)
     */
    public static final String WEB_CONTEXT_SETTING_ACADEMY_TRACK = "UserAssistantAcademyTrack";

    /**
     * Contains the name of the attribute used to specify the video code to link to.
     *
     * @see WebContext#setAttribute(String, Object)
     */
    public static final String WEB_CONTEXT_SETTING_ACADEMY_VIDEO = "UserAssistantAcademyVideo";

    /**
     * Contains the name of the attribute used to specify the KBA to link to.
     *
     * @see WebContext#setAttribute(String, Object)
     */
    public static final String WEB_CONTEXT_SETTING_KBA = "UserAssistantKba";

    private static final Pattern VALID_PATH = Pattern.compile("[a-zA-Z0-9\\-/_]+");

    /**
     * Determines the {@link sirius.biz.tycho.academy.OnboardingEngine academy} track to link to.
     *
     * @return the id of the track or <tt>null</tt> to skip linking to the academy
     */
    public String fetchAcademyTrack() {
        WebContext webContext = WebContext.getCurrent();
        if (!webContext.isValid()) {
            return null;
        }

        return webContext.safeGet(WEB_CONTEXT_SETTING_ACADEMY_TRACK)
                         .asOptionalString()
                         .or(() -> getSettingIfPresent("academy-track", webContext.getRequestedURI()))
                         .orElse(null);
    }

    private Optional<String> getSettingIfPresent(String type, String path) {
        if (!VALID_PATH.matcher(path).matches()) {
            return Optional.empty();
        }
        String setting = "user-assistant." + type + "." + path;

        try {
            if (Sirius.getSettings().has(setting)) {
                return Sirius.getSettings().get(setting).asOptionalString();
            } else {
                return Optional.empty();
            }
        } catch (ConfigException _) {
            return Optional.empty();
        }
    }

    /**
     * Determines the {@link sirius.biz.tycho.academy.OnboardingEngine academy} video to link to.
     *
     * @return the code of the video or <tt>null</tt> to skip linking to the academy
     */
    public String fetchAcademyVideo() {
        WebContext webContext = WebContext.getCurrent();
        if (!webContext.isValid()) {
            return null;
        }

        return webContext.safeGet(WEB_CONTEXT_SETTING_ACADEMY_VIDEO)
                         .asOptionalString()
                         .or(() -> getSettingIfPresent("academy-video", webContext.getRequestedURI()))
                         .orElse(null);
    }

    /**
     * Determines the {@link sirius.biz.tycho.kb.KnowledgeBase} article to link to.
     *
     * @return the code of the KBA or <tt>null</tt> to skip linking
     */
    public String fetchKba() {
        WebContext webContext = WebContext.getCurrent();
        if (!webContext.isValid()) {
            return null;
        }

        return webContext.safeGet(WEB_CONTEXT_SETTING_KBA)
                         .asOptionalString()
                         .or(() -> getSettingIfPresent("kba", webContext.getRequestedURI()))
                         .orElse(null);
    }
}
