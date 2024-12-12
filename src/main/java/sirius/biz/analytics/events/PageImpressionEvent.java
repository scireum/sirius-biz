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
import sirius.db.mixing.annotations.NullAllowed;
import sirius.kernel.commons.Strings;
import sirius.web.controller.ControllerDispatcher;
import sirius.web.http.WebContext;

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
public class PageImpressionEvent extends Event<PageImpressionEvent> implements UserEvent {

    /**
     * Contains a generic or shortened URI which can be used to aggregate on.
     * <p>
     * If, for example a web shop would record views of items with urls like "/item/0815" and "/item/0816", these
     * would end up in {@link WebData#URL}. However, to sum up the total view of items one could use "/item/:1" as
     * <b>aggregationUri</b>.
     * <p>
     * If no explicit value is given, but a controller {@link sirius.web.controller.Routed route} is hit,
     * its pattern will be used.
     * <p>
     * Note that the <tt>action</tt> field can also be used to store an additional event type / meta information.
     */
    public static final Mapping AGGREGATION_URI = Mapping.named("aggregationUri");
    private String aggregationUri;

    /**
     * Contains the effectively requested URI.
     * <p>
     * If not explicit value is given, we use the {@link WebContext#getRequestedURI() requested URI}.
     */
    public static final Mapping URI = Mapping.named("uri");
    private String uri;

    /**
     * Permits store a custom value to further distinguish events.
     * <p>
     * Can be used by callers to collect further metadata for each event.
     */
    public static final Mapping ACTION = Mapping.named("action");
    @NullAllowed
    private String action;

    /**
     * Permits to store the <tt>unique object name</tt> of the main data object being accessed.
     * <p>
     * This can be used to directly store a database id or otherwise unique identifier in the
     * event, in case the <tt>uri</tt> doesn't contain enough data or is inconvenient to use.
     */
    public static final Mapping DATA_OBJECT = Mapping.named("dataObject");
    @NullAllowed
    private String dataObject;

    /**
     * Contains the current user, tenant and scope if available.
     */
    private final UserData userData = new UserData();

    /**
     * Contains metadata about the HTTP request (user-agent, url).
     */
    public static final Mapping WEB_DATA = Mapping.named("webData");
    private final WebData webData = new WebData();

    @BeforeSave
    protected void fillAndCheck() {
        if (Strings.isEmpty(uri) || Strings.isEmpty(aggregationUri)) {
            WebContext webContext = WebContext.getCurrent();
            if (webContext.isValid()) {
                if (Strings.isEmpty(uri)) {
                    uri = webContext.getRequestedURI();
                }
                if (Strings.isEmpty(aggregationUri)) {
                    aggregationUri = webContext.get(ControllerDispatcher.ATTRIBUTE_MATCHED_ROUTE).getString();
                }
            }
        }

        if (Strings.isEmpty(uri)) {
            throw new IllegalArgumentException("Please provide a URI");
        }
        if (Strings.isEmpty(aggregationUri)) {
            throw new IllegalArgumentException("Please provide an aggregation URI");
        }
    }

    /**
     * Overwrites the URI to use.
     * <p>
     * Note that most commonly, this will be filled from the available <tt>WebContext</tt> and there is no need to
     * set it manually
     *
     * @param uri the URI to use
     * @return the event itself for fluent method calls
     * @see #uri
     */
    public PageImpressionEvent withUri(String uri) {
        this.uri = uri;
        return this;
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

    /**
     * Adds a custom action or other identifier to the event.
     *
     * @param action a specific action name or other tag value which is recorded as metadata
     * @return the event itself for fluent method calls
     */
    public PageImpressionEvent withAction(String action) {
        this.action = action;
        return this;
    }

    /**
     * Adds the <tt>unique object name</tt> (or any other database id / identifier) of a referenced data object.
     * <p>
     * This permits to associate the event with a data object and might help to better query recorded events.
     *
     * @param uniqueObjectName the name or id of the associated data object
     * @return the event itself for fluent method calls
     */
    public PageImpressionEvent withDataObject(String uniqueObjectName) {
        this.dataObject = uniqueObjectName;
        return this;
    }

    public String getAggregationUri() {
        return aggregationUri;
    }

    @Override
    public UserData getUserData() {
        return userData;
    }

    public WebData getWebData() {
        return webData;
    }
}
