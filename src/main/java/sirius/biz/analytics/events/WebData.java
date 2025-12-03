/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.analytics.events;

import sirius.db.mixing.Composite;
import sirius.db.mixing.Mapping;
import sirius.db.mixing.annotations.BeforeSave;
import sirius.db.mixing.annotations.Length;
import sirius.db.mixing.annotations.NullAllowed;
import sirius.kernel.commons.Strings;
import sirius.web.http.UserAgent;
import sirius.web.http.WebContext;

/**
 * Can be embedded into an {@link Event} to record some interesting parts of the current {@link WebContext}.
 */
public class WebData extends Composite {

    /**
     * Stores the requested URL.
     */
    public static final Mapping URL = Mapping.named("url");
    @NullAllowed
    private String url;

    /**
     * Stores the User-Agent.
     */
    public static final Mapping USER_AGENT = Mapping.named("userAgent");
    @NullAllowed
    private String userAgent;

    /**
     * Determines the HTTP request method (GET, POST, etc.).
     */
    public static final Mapping REQUEST_METHOD = Mapping.named("requestMethod");
    @NullAllowed
    private String requestMethod;

    /**
     * Determines if the User-Agent was an iOS device.
     */
    public static final Mapping IOS = Mapping.named("ios");
    private boolean ios;

    /**
     * Determines if the User-Agent was an Android device.
     */
    public static final Mapping ANDROID = Mapping.named("android");
    private boolean android;

    /**
     * Determines if the User-Agent was a mobile device.
     */
    public static final Mapping MOBILE = Mapping.named("mobile");
    private boolean mobile;

    /**
     * Determines if the User-Agent was a mobile phone.
     */
    public static final Mapping PHONE = Mapping.named("phone");
    private boolean phone;

    /**
     * Determines if the User-Agent was a tablet phone.
     */
    public static final Mapping TABLET = Mapping.named("tablet");
    private boolean tablet;

    /**
     * Determines if the User-Agent was a desktop computer.
     */
    public static final Mapping DESKTOP = Mapping.named("desktop");
    private boolean desktop;

    /**
     * Stores the response time (which is actually the TTFB - the time the server took to generate the first byte of
     * the response. This way, we really measure the server performance and not the up- or downstream bandwidth).
     */
    public static final Mapping RESPONSE_TIME = Mapping.named("responseTime");
    @Length(4)
    @NullAllowed
    private Long responseTime;

    @BeforeSave
    protected void fill() {
        WebContext webContext = WebContext.getCurrent();
        if (webContext.isValid()) {
            if (Strings.isEmpty(url)) {
                url = webContext.getRequestedURL();
            }
            if (Strings.isEmpty(userAgent)) {
                persistUserAgent(webContext.getUserAgent());
            }
            if (webContext.getTTFBMillis() > 0 && responseTime == null) {
                responseTime = webContext.getTTFBMillis();
            }
            requestMethod = webContext.getRequest().method().name();
        }
    }

    protected void persistUserAgent(UserAgent userAgent) {
        this.userAgent = userAgent.getUserAgentString();
        this.android = userAgent.isAndroid();
        this.ios = userAgent.isIOS();
        this.mobile = userAgent.isMobile();
        this.phone = userAgent.isPhone();
        this.tablet = userAgent.isTablet();
        this.desktop = userAgent.isDesktop();
    }

    /**
     * Specifies a custom URL to record.
     * <p>
     * In most cases this method shouldn't be called manually as the event will initialize this field with
     * the currently requested URL (as indicated by the {@link WebContext}).
     *
     * @param url the url to store
     */
    public void setCustomUrl(String url) {
        this.url = url;
    }

    /**
     * Specifies a custom user agent to record.
     * <p>
     * In most cases this method shouldn't be called manually as the event will initialize this field with
     * the current {@link UserAgent} (as indicated by the {@link WebContext}).
     *
     * @param userAgent the user agent to store
     */
    public void setCustomUserAgent(UserAgent userAgent) {
        persistUserAgent(userAgent);
    }

    /**
     * Specifies a custom response time to record.
     * <p>
     * In most cases this method shouldn't be called manually as the event will initialize this field with
     * the current <b>TTFB</b> (time to first byte) (as indicated by the {@link WebContext}).
     *
     * @param responseTime the response time to store
     */
    public void setCustomResponseTime(long responseTime) {
        this.responseTime = responseTime;
    }

    public String getUrl() {
        return url;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public String getRequestMethod() {
        return requestMethod;
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
