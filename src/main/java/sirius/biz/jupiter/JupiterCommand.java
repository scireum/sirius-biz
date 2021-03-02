/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.jupiter;

import redis.clients.jedis.commands.ProtocolCommand;
import redis.clients.jedis.util.SafeEncoder;
import sirius.kernel.commons.Explain;

/**
 * Provides a custom command as defined by Jupiter.
 */
public class JupiterCommand implements ProtocolCommand {

    private final String command;
    private final byte[] rawCommand;

    /**
     * Wraps the given command name into a command.
     *
     * @param command the name of the command to represent
     */
    public JupiterCommand(String command) {
        this.command = command;
        this.rawCommand = SafeEncoder.encode(command);
    }

    @SuppressWarnings("AssignmentOrReturnOfFieldWithMutableType")
    @Explain("For performance reasons, we do not clone the array here and trust the Jedis client to behave.")
    @Override
    public byte[] getRaw() {
        return rawCommand;
    }

    @Override
    public String toString() {
        return command;
    }
}
