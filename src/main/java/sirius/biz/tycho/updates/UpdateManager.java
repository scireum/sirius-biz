/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.tycho.updates;

import sirius.biz.analytics.events.EventRecorder;
import sirius.biz.analytics.events.UserData;
import sirius.db.jdbc.OMA;
import sirius.kernel.commons.Limit;
import sirius.kernel.commons.Strings;
import sirius.kernel.di.std.ConfigValue;
import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.Register;
import sirius.kernel.health.Exceptions;
import sirius.kernel.health.Log;
import sirius.kernel.xml.StructuredNode;
import sirius.kernel.xml.XMLCall;
import sirius.web.security.UserInfo;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Reads a given <tt>ATOM</tt> feed and serves the recorded items as {@link UpdateInfo updates} to the users of the
 * system.
 */
@Register(classes = UpdateManager.class)
public class UpdateManager {

    private static final String ATOM_FEED_CHANNEL = "channel";
    private static final String ATOM_FEED_ITEM = "item";
    private static final String WP_EXTENSION_POST_ID = "post-id";
    private static final String ATOM_ITEM_GUID = "guid";
    private static final String ATOM_ITEM_PUB_DATE = "pubDate";
    private static final String ATOM_ITEM_TITLE = "title";
    private static final String ATOM_ITEM_DESCRIPTION = "description";
    private static final String ATOM_ITEM_LINK = "link";
    private static final String ATOM_ITEM_CATEGORY = "category";

    private static final int FEED_FETCH_INTERVAL_HOURS = 1;
    private static final int FEED_FETCH_RETRY_MINUTES = 15;
    private static final int MAX_FEED_ITEMS_TO_FETCH = 3;
    private static final long MAX_AGE_MONTHS = 3;

    @ConfigValue("tycho.updates.feedUrl")
    private String feedUrl;

    @ConfigValue("tycho.updates.triggerCategories")
    private List<String> triggerCategories;

    @ConfigValue("tycho.updates.importantCategories")
    private List<String> importantCategories;

    @Part
    private EventRecorder eventRecorder;

    @Part
    private OMA oma;

    private List<UpdateInfo> globalUpdates = Collections.emptyList();
    private LocalDateTime lastFetch;
    private LocalDateTime lastAttempt;

    /**
     * Fetches all currently available updates.
     * <p>
     * Note that this can be called frequently, as the feed results are cached locally.
     *
     * @return the list of available updates.
     */
    public List<UpdateInfo> fetchUpdates() {
        if (Strings.isEmpty(feedUrl)) {
            return Collections.emptyList();
        }

        LocalDateTime fetchLimit = LocalDateTime.now().minusHours(FEED_FETCH_INTERVAL_HOURS);
        LocalDateTime retryLimit = LocalDateTime.now().minusMinutes(FEED_FETCH_RETRY_MINUTES);
        if ((lastFetch == null || lastFetch.isBefore(fetchLimit)) && (lastAttempt == null || lastAttempt.isBefore(
                retryLimit))) {
            fetchUpdatesFromFeed();
        }

        return Collections.unmodifiableList(globalUpdates);
    }

    private void fetchUpdatesFromFeed() {
        try {
            this.lastAttempt = LocalDateTime.now();
            List<UpdateInfo> nextUpdates = new ArrayList<>();
            XMLCall call = XMLCall.to(new URI(feedUrl));
            Limit limit = new Limit(0, MAX_FEED_ITEMS_TO_FETCH);
            for (StructuredNode node : call.getInput().getNode(ATOM_FEED_CHANNEL).queryNodeList(ATOM_FEED_ITEM)) {
                List<String> categories = node.queryNodeList(ATOM_ITEM_CATEGORY)
                                              .stream()
                                              .map(category -> category.queryString("."))
                                              .collect(Collectors.toList());
                UpdateInfo updateInfo = parseFeedItem(node, categories);
                if (isTriggerCategoryPresent(categories) && !isOutdated(updateInfo) && limit.nextRow()) {
                    nextUpdates.add(updateInfo);
                }
            }
            this.globalUpdates = nextUpdates;
            this.lastFetch = LocalDateTime.now();
        } catch (IOException | URISyntaxException e) {
            Exceptions.handle()
                      .error(e)
                      .to(Log.BACKGROUND)
                      .withSystemErrorMessage("Failed to fetch updates feed: %s (%s)")
                      .handle();
        }
    }

    private boolean isOutdated(UpdateInfo updateInfo) {
        return updateInfo.getCreated().isBefore(LocalDateTime.now().minusMonths(MAX_AGE_MONTHS));
    }

    private boolean isTriggerCategoryPresent(List<String> categories) {
        return this.triggerCategories.isEmpty() || categories.stream().anyMatch(this.triggerCategories::contains);
    }

    private UpdateInfo parseFeedItem(StructuredNode node, List<String> categories) {
        String guid = node.queryValue(WP_EXTENSION_POST_ID).asString(node.queryString(ATOM_ITEM_GUID));
        LocalDateTime pubDate =
                LocalDateTime.from(DateTimeFormatter.RFC_1123_DATE_TIME.parse(node.queryString(ATOM_ITEM_PUB_DATE)));

        UpdateInfo updateInfo = new UpdateInfo(guid,
                                               pubDate,
                                               node.queryString(ATOM_ITEM_TITLE),
                                               node.queryString(ATOM_ITEM_DESCRIPTION),
                                               node.queryString(ATOM_ITEM_LINK));
        if (categories.stream().anyMatch(this.importantCategories::contains)) {
            updateInfo.markImportant();
        }
        return updateInfo;
    }

    /**
     * Forces the feed to be reloaded on the next call to {@link #fetchUpdates()}.
     * <p>
     * This can be used to forcefully refresh the feed data without waiting for the natural reload. This method is
     * intended to be called via <tt>/system/scripting</tt> when needed.
     */
    public void purgeFeed() {
        this.lastAttempt = null;
        this.lastFetch = null;
    }

    /**
     * Determines if the given user read (clicked) the given update item.
     * <p>
     * This is done by searching for a {@link UpdateClickEvent}. Note that this isn't supported by an index and
     * thus shouldn't be used heavily (i.e. in the frontend). This is rather intended to be used by the analytics
     * framework to decide if and when a user must be notified about updates via an email.
     *
     * @param userId     the id of the user to check. This must be the format as given in {@link UserInfo#getUserId()}
     * @param updateGuid the {@link UpdateInfo#getGuid()} to check
     * @return <tt>true</tt> if a click event was recorded, <tt>false</tt> otherwise
     */
    public boolean isRead(String userId, String updateGuid) {
        if (!eventRecorder.isConfigured()) {
            return false;
        }

        return oma.select(UpdateClickEvent.class)
                  .eq(UpdateClickEvent.USER_DATA.inner(UserData.USER_ID), userId)
                  .eq(UpdateClickEvent.UPDATE_GUID, updateGuid)
                  .exists();
    }
}
