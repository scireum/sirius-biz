/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.web;

import sirius.kernel.commons.Strings;
import sirius.kernel.di.std.Register;
import sirius.kernel.health.Exceptions;
import sirius.kernel.nls.NLS;
import sirius.web.controller.Routed;
import sirius.web.http.WebContext;
import sirius.web.security.MaintenanceInfo;
import sirius.web.security.Permission;
import sirius.web.security.UserContext;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * In charge of enabling or disabling the <b>Disaster Mode</b> for the backend.
 * <p>
 * Using this, the backend ({@link sirius.web.security.ScopeInfo#DEFAULT_SCOPE} can display a message to announce
 * a planned maintenance and also can be locked entirely for non-admin users.
 *
 * @see DisasterModeInfo
 */
@Register
public class DisasterController extends BizController {

    protected static final String URI_DISASTER = "/disaster";
    protected static final String URI_SYSTEM_DISASTER = "/system/disaster";

    public static final String PERMISSION_CONTROL_DISASTER_MODE = "permission-control-disaster-mode";

    /**
     * Provides a management interface to enable and disable the maintenance / disaster mode.
     * <p>
     * This is a "system" URI in case that <tt>/disaster</tt> is already used by the application itself.
     *
     * @param webContext the current request
     */
    @Routed(value = URI_SYSTEM_DISASTER, priority = 900)
    @Permission(PERMISSION_CONTROL_DISASTER_MODE)
    public void systemDisaster(WebContext webContext) {
        disaster(webContext);
    }

    /**
     * Provides a management interface to enable and disable the maintenance / disaster mode.
     * <p>
     * This is a user friendly short URI. Note that <tt>/system/disaster</tt> can also be used in case this URI is
     * already used by the application
     *
     * @param webContext the current request
     */
    @Routed(value = URI_DISASTER, priority = 900)
    @Permission(PERMISSION_CONTROL_DISASTER_MODE)
    public void disaster(WebContext webContext) {
        MaintenanceInfo maintenanceInfo = UserContext.getCurrentScope().tryAs(MaintenanceInfo.class).orElse(null);
        if (!(maintenanceInfo instanceof DisasterModeInfo disasterModeInfo)) {
            throw Exceptions.createHandled()
                            .withSystemErrorMessage(
                                    "A different MaintenanceInfo is active - cannot show or change settings.")
                            .handle();
        }

        if (webContext.isSafePOST()) {
            try {
                disasterModeInfo.updateMode(webContext.get("locked").asBoolean(),
                                            parseLocalDateTime(webContext, "lockDate", "lockTime"),
                                            parseLocalDateTime(webContext, "messageDate", "messageTime"),
                                            webContext.get("previewMessage").getString(),
                                            webContext.get("lockMessage").getString());
                showSavedMessage();
            } catch (Exception e) {
                handle(e);
            }
        }

        webContext.respondWith().template("/templates/biz/disaster.html.pasta", disasterModeInfo);
    }

    private LocalDateTime parseLocalDateTime(WebContext webContext, String date, String time) {
        String dateValue = webContext.get(date).getString();
        String timeValue = webContext.get(time).getString();

        if (Strings.isEmpty(dateValue) || Strings.isEmpty(timeValue)) {
            return null;
        }

        return NLS.parseUserString(LocalDate.class, dateValue).atTime(NLS.parseUserString(LocalTime.class, timeValue));
    }
}
