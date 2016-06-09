package sirius.biz.i5;

import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.Register;
import sirius.kernel.health.console.Command;

import javax.annotation.Nonnull;
import java.lang.ref.WeakReference;

/**
 * Provides a list of active connections for all configured I5 connection pools.
 */
@Register
public class I5Command implements Command {

    @Part
    private I5Connector i5;

    @Override
    public void execute(Output output, String... strings) throws Exception {
        for (I5ConnectionPool pool : i5.pools.values()) {
            output.line(pool.toString());
            output.separator();
            for (WeakReference<I5Connection> c : pool.openConnections) {
                I5Connection connection = c.get();
                if (connection != null) {
                    output.apply("%-25s %s", connection.getLastUse(), connection.getLastJob());
                }
            }
            output.blankLine();
        }
    }

    @Override
    public String getDescription() {
        return "List all active connections to an IBM i5";
    }

    @Nonnull
    @Override
    public String getName() {
        return "i5";
    }
}
