/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.analytics.events;

public class TestEvent2 extends Event {

    private final UserData user = new UserData();

    public UserData getUser() {
        return user;
    }
}
