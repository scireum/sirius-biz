/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.analytics.events;

import sirius.db.mixing.Composite;
import sirius.db.mixing.annotations.BeforeSave;
import sirius.db.mixing.annotations.Length;
import sirius.db.mixing.annotations.NullAllowed;
import sirius.kernel.async.CallContext;
import sirius.kernel.commons.Strings;
import sirius.web.http.WebContext;

/**
 * Can be embedded into an {@link Event} to record some interesting parts of the current {@link WebContext}.
 */
public class WebData extends Composite {

    /**
     * Stores the requested URL.
     */
    @Length(255)
    @NullAllowed
    private String url;

    /**
     * Stores the User-Agent.
     */
    @Length(255)
    @NullAllowed
    private String userAgent;

    /**
     * Determines if the User-Agent was an iOS device.
     */
    private boolean ios;

    /**
     * Determines if the User-Agent was an Android device.
     */
    private boolean android;

    /**
     * Determines if the User-Agent was a mobile device.
     */
    private boolean mobile;

    /**
     * Determines if the User-Agent was a mobile phone.
     */
    private boolean phone;

    /**
     * Determines if the User-Agent was a tablet phone.
     */
    private boolean tablet;

    /**
     * Determines if the User-Agent was a desktop computer.
     */
    private boolean desktop;

    /**
     * Stores the response time (which is actually the TTFB - the time the server took to generate the first byte of
     * the response. This way, we really measure the server performance and not the up- or downstream bandwidth).
     */
    @Length(4)
    @NullAllowed
    private Long responseTime;

    @BeforeSave
    protected void fill() {
        WebContext ctx = CallContext.getCurrent().get(WebContext.class);
        if (ctx.isValid()) {
            url = Strings.limit(ctx.getRequestedURL(), 255, false);
            userAgent = Strings.limit(ctx.getUserAgent().getUserAgentString(), 255, false);
            android = ctx.getUserAgent().isAndroid();
            ios = ctx.getUserAgent().isIOS();
            mobile = ctx.getUserAgent().isMobile();
            phone = ctx.getUserAgent().isPhone();
            tablet = ctx.getUserAgent().isTablet();
            desktop = ctx.getUserAgent().isDesktop();
            if (ctx.getTTFBMillis() > 0) {
                responseTime = ctx.getTTFBMillis();
            }
        }
    }

    public String getUrl() {
        return url;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public boolean isIos() {
        return ios;
    }

    public boolean isAndroid() {
        return android;
    }

    public boolean isMobile() {
        return mobile;
    }

    public boolean isPhone() {
        return phone;
    }

    public boolean isTablet() {
        return tablet;
    }

    public boolean isDesktop() {
        return desktop;
    }

    public long getResponseTime() {
        return responseTime;
    }
}
