/*
 * Made with all the love in the world
 * by scireum in Stuttgart, Germany
 *
 * Copyright by scireum GmbH
 * https://www.scireum.de - info@scireum.de
 */

package sirius.biz.analytics.events;

public class TestEvent2 extends Event<TestEvent2> {

    private final UserData user = new UserData();

    public UserData getUser() {
        return user;
    }
}
