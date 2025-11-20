/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.web;

import com.rometools.rome.feed.synd.SyndFeed;
import com.rometools.rome.feed.synd.SyndFeedImpl;
import com.rometools.rome.io.SyndFeedInput;
import sirius.kernel.commons.Outcall;
import sirius.kernel.health.Exceptions;
import sirius.kernel.health.Log;

import java.io.StringReader;
import java.net.URI;

/**
 * Helps to read RSS feeds and provides access to their content wrapped into {@link SyndFeed} objects
 */
public class RssFeedHelper {

    /**
     * Retrieves a {@link SyndFeed} of the given URL
     *
     * @param feedUrl the URL to fetch the RSS feed from
     * @return a SyndFeed instance for the given URL
     */
    public SyndFeed processFeed(String feedUrl) {
        try {
            Outcall outcall = new Outcall(new URI(feedUrl));
            return new SyndFeedInput().build(new StringReader(outcall.getData()));
        } catch (Exception exception) {
            Exceptions.handle(Log.APPLICATION, exception);
            return new SyndFeedImpl();
        }
    }
}
