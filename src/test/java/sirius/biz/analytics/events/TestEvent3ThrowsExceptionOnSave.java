package sirius.biz.analytics.events;

import sirius.db.mixing.annotations.BeforeSave;
import sirius.kernel.health.Exceptions;

import java.io.UncheckedIOException;

public class TestEvent3ThrowsExceptionOnSave extends Event {

    private final UserData user = new UserData();

    public UserData getUser() {
        return user;
    }

    @BeforeSave
    public void throwErrorOnSave() {
        throw Exceptions.createHandled().handle();
    }
}
