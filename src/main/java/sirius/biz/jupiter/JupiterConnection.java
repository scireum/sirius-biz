/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.jupiter;

import redis.clients.jedis.CommandArguments;
import redis.clients.jedis.UnifiedJedis;
import redis.clients.jedis.commands.ProtocolCommand;
import redis.clients.jedis.util.SafeEncoder;

import java.util.List;

/**
 * Provides raw command access to a {@link UnifiedJedis} while emulating the request/response API which was
 * previously provided by the low-level {@code redis.clients.jedis.Connection}.
 * <p>
 * Since Jedis 7.5, {@code RedisDB} hands out a managed, shared {@link UnifiedJedis} instead of a poolable
 * connection. As {@link UnifiedJedis} executes each command atomically (borrow a connection, write, read, return),
 * the classic two-step pattern of first sending a command and then fetching the reply no longer maps to a single
 * connection. This adapter bridges that gap by executing the command in {@link #sendCommand} and buffering the raw
 * reply so that it can subsequently be retrieved via the various {@code getXXXReply} methods used throughout the
 * Jupiter package.
 * <p>
 * The raw reply produced by {@link UnifiedJedis#sendCommand} uses the identical wire representation (RESP2) as the
 * former {@code Connection}: simple and bulk strings are returned as <tt>byte[]</tt>, integers as <tt>Long</tt> and
 * arrays as <tt>List</tt>. Therefore, all existing parsing (see {@link Jupiter#read}) remains valid.
 * <p>
 * An instance is stateful (it buffers the last reply) and therefore must not be shared across threads. A fresh
 * instance is created for every command execution by {@link JupiterConnector}.
 */
public class JupiterConnection {

    private final UnifiedJedis jedis;
    private Object lastReply;

    protected JupiterConnection(UnifiedJedis jedis) {
        this.jedis = jedis;
    }

    /**
     * Executes the given command and buffers its reply for later retrieval.
     *
     * @param command the command to execute
     * @param args    the arguments to pass to the command
     */
    public void sendCommand(ProtocolCommand command, String... args) {
        this.lastReply = jedis.executeCommand(new CommandArguments(command).addObjects((Object[]) args));
    }

    /**
     * Returns the buffered reply of the last command interpreted as a status code (simple string).
     *
     * @return the status code reply or <tt>null</tt> if none was received
     */
    public String getStatusCodeReply() {
        return decodeString(lastReply);
    }

    /**
     * Returns the buffered reply of the last command interpreted as a bulk string.
     *
     * @return the bulk string reply or <tt>null</tt> if none was received
     */
    public String getBulkReply() {
        return decodeString(lastReply);
    }

    /**
     * Returns the buffered reply of the last command interpreted as an integer.
     *
     * @return the integer reply or <tt>null</tt> if none was received
     */
    public Long getIntegerReply() {
        return (Long) lastReply;
    }

    /**
     * Returns the buffered reply of the last command interpreted as an array of arbitrary objects.
     *
     * @return the array reply as a list of raw objects
     */
    @SuppressWarnings("unchecked")
    public List<Object> getObjectMultiBulkReply() {
        return (List<Object>) lastReply;
    }

    /**
     * Returns the buffered reply of the last command interpreted as an array of integers.
     *
     * @return the array reply as a list of integers
     */
    @SuppressWarnings("unchecked")
    public List<Long> getIntegerMultiBulkReply() {
        return (List<Long>) lastReply;
    }

    /**
     * Returns the raw buffered reply of the last command.
     *
     * @return the raw reply object which is either a <tt>byte[]</tt>, a <tt>Long</tt> or a <tt>List</tt>
     */
    public Object getOne() {
        return lastReply;
    }

    private static String decodeString(Object reply) {
        if (reply == null) {
            return null;
        }

        return SafeEncoder.encode((byte[]) reply);
    }
}
