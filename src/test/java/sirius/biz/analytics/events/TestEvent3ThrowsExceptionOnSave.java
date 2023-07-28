package sirius.biz.analytics.events;

import sirius.db.mixing.annotations.BeforeSave;
import sirius.kernel.health.Exceptions;

public class TestEvent3ThrowsExceptionOnSave extends Event<TestEvent3ThrowsExceptionOnSave> {

    private final UserData user = new UserData();

    public UserData getUser() {
        return user;
    }

    @BeforeSave
    protected void throwErrorOnSave() {
        throw Exceptions.createHandled().handle();
    }
}
