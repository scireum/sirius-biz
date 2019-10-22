/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.analytics.events;

import sirius.db.mixing.Mapping;
import sirius.db.mixing.annotations.BeforeSave;
import sirius.kernel.commons.Strings;

/**
 * Records a page view or page impression.
 * <p>
 * This can be used either by the backend (aka {@link sirius.web.security.ScopeInfo#DEFAULT_SCOPE}) or within
 * other scopes.
 * <p>
 * Recording a page impression is as easy as:
 * <pre>{@code
 *  @Part
 *  EventRecorder recorder;
 *
 *  void doStuff() {
 *      recorder.record(new PageImpressionEvent().withAggregationUrl("/my/lovely/page"));
 *  }
 *
 * }</pre>
 *
 * @see EventRecorder
 * @see #withAggregationUrl(String)
 */
public class PageImpressionEvent extends Event {

    /**
     * Contains a generic or shortened URL which can be used to aggregate on.
     * <p>
     * If, for example a web shop would record views of items with urls like "/item/0815" and "/item/0816", these
     * would end up in {@link WebData#URL}. However, to sum up the total view of items one could use "/item" as
     * <b>aggregationUri</b>.
     */
    public static final Mapping AGGREGATION_URI = Mapping.named("aggregationUri");
    private String aggregationUri;

    /**
     * Contains the current user, tenant and scope if available.
     */
    public static final Mapping USER_DATA = Mapping.named("userData");
    private final UserData userData = new UserData();

    /**
     * Contains metadata about the HTTP request (user-agent, url).
     */
    public static final Mapping WEB_DATA = Mapping.named("webData");
    private final WebData webData = new WebData();

    @BeforeSave
    protected void check() {
        if (Strings.isEmpty(aggregationUri)) {
            throw new IllegalArgumentException("Please provide an aggregation URI");
        }
    }

    /**
     * Specifies the aggregation URL to use.
     *
     * @param aggregationUri a shortened or generic URI
     * @return the event itself for fluent method calls
     * @see #aggregationUri
     */
    public PageImpressionEvent withAggregationUrl(String aggregationUri) {
        this.aggregationUri = aggregationUri;
        return this;
    }

    public String getAggregationUri() {
        return aggregationUri;
    }

    public UserData getUserData() {
        return userData;
    }

    public WebData getWebData() {
        return webData;
    }
}
