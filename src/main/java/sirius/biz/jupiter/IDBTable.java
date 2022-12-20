/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.jupiter;

import redis.clients.jedis.Connection;
import sirius.kernel.commons.Limit;
import sirius.kernel.commons.PullBasedSpliterator;
import sirius.kernel.commons.Strings;
import sirius.kernel.commons.Value;
import sirius.kernel.commons.Values;
import sirius.kernel.nls.NLS;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.Spliterator;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * Wraps an InfoGraphDB table of an attached Jupiter instance.
 */
public class IDBTable {

    private static final JupiterCommand CMD_LOOKUP = new JupiterCommand("IDB.LOOKUP");
    private static final JupiterCommand CMD_ILOOKUP = new JupiterCommand("IDB.ILOOKUP");
    private static final JupiterCommand CMD_QUERY = new JupiterCommand("IDB.QUERY");
    private static final JupiterCommand CMD_IQUERY = new JupiterCommand("IDB.IQUERY");
    private static final JupiterCommand CMD_SEARCH = new JupiterCommand("IDB.SEARCH");
    private static final JupiterCommand CMD_ISEARCH = new JupiterCommand("IDB.ISEARCH");
    private static final JupiterCommand CMD_SCAN = new JupiterCommand("IDB.SCAN");
    private static final JupiterCommand CMD_ISCAN = new JupiterCommand("IDB.ISCAN");
    private static final JupiterCommand CMD_LEN = new JupiterCommand("IDB.LEN");

    private static final int FETCH_PAGE_SIZE = 250;

    private final JupiterConnector jupiter;
    private final String name;

    /**
     * Creates a new wrapper for a given Jupiter instance and table name.
     *
     * @param jupiter the jupiter connection to wrap
     * @param name    the name of the table to wrap
     */
    public IDBTable(JupiterConnector jupiter, String name) {
        this.jupiter = jupiter;
        this.name = name;
    }

    /**
     * Provides a tool to fluently build queries.
     */
    public class QueryBuilder {

        private boolean exact;
        private String searchPaths;
        private String searchValue;
        private String primaryLang;
        private String fallbackLang;

        /**
         * Specifies the paths to look for a given value.
         * <p>
         * Note that this will perform an exact search (the search value has to match the value in the field).
         *
         * @param paths one or more paths to search in
         * @return the builder itself for fluent method calls
         */
        public QueryBuilder lookupPaths(String... paths) {
            if (paths == null || paths.length == 0) {
                this.searchPaths = null;
            } else {
                this.exact = true;
                this.searchPaths = Strings.join(",", paths);
            }

            return this;
        }

        /**
         * Specifies the paths to search for a given value.
         * <p>
         * Note that this will perform a loose search which will match case insensitive and also prefixes or tokens
         * within a field value. (Tokens are more or less words or other text parts separated by whitespaces and
         * other common punctuation).
         * <p>
         * Also note that {@link #searchPaths(String...)} and {@link #lookupPaths(String...)} overwrite each other.
         * <p>
         * Keep in mind, that only paths which are listed in "fulltextIndices" can be used.
         *
         * @param paths the paths to search in
         * @return the builder itself for fluent method calls
         */
        public QueryBuilder searchPaths(String... paths) {
            if (paths == null || paths.length == 0) {
                this.searchPaths = null;
            } else {
                this.exact = false;
                this.searchPaths = Strings.join(",", paths);
            }

            return this;
        }

        /**
         * Performs a search in all fulltext fields.
         *
         * @return the builder itself for fluent method calls
         */
        public QueryBuilder searchInAllFields() {
            return searchPaths("*");
        }

        /**
         * Specifies the value to search for.
         * <p>
         * Note that in order for this to work, either {@link #lookupPaths(String...)}, {@link #searchPaths(String...)}
         * or {@link #searchInAllFields()} has to be used to specify where to search.
         *
         * @param value the value to search for
         * @return the builder itself for fluent method calls
         */
        public QueryBuilder searchValue(@Nullable String value) {
            this.searchValue = value;
            return this;
        }

        /**
         * Specifies the translation languages to use for translatable fields.
         *
         * @param primaryLang  the first language to attempt
         * @param fallbackLang the fallback language to attempt
         * @return the builder itself for fluent method calls
         */
        public QueryBuilder translate(String primaryLang, String fallbackLang) {
            this.primaryLang = primaryLang;
            this.fallbackLang = fallbackLang;
            return this;
        }

        /**
         * Specifies a single language to use for translatable fields.
         *
         * @param lang the language to use for lookups
         * @return the builder itself for fluent method calls
         */
        public QueryBuilder translate(String lang) {
            this.primaryLang = lang;
            this.fallbackLang = lang;
            return this;
        }

        /**
         * Uses the currently active languages for translatable fields.
         * <p>
         * This is boilerplate for {@code translate(NLS.getCurrentLanguage(), NLS.getFallbackLanguage())}.
         *
         * @return the builder itself for fluent method calls
         */
        public QueryBuilder translate() {
            this.primaryLang = NLS.getCurrentLanguage();
            this.fallbackLang = NLS.getFallbackLanguage();
            return this;
        }

        /**
         * Queries a single row.
         *
         * @param pathsToQuery the paths to return
         * @return the result row wrapped ad optional
         */
        public Optional<Values> singleRow(String... pathsToQuery) {
            checkConstraints();

            if (exact && Strings.isFilled(searchValue)) {
                if (Strings.isFilled(primaryLang)) {
                    return ilookup(primaryLang, fallbackLang, searchPaths, searchValue, pathsToQuery);
                } else {
                    return lookup(searchPaths, searchValue, pathsToQuery);
                }
            }

            return execute(Limit.singleItem(), pathsToQuery).stream().findFirst();
        }

        private void checkConstraints() {
            if (Strings.isFilled(searchValue) && !Strings.isFilled(searchPaths)) {
                throw new IllegalStateException(Strings.apply(
                        "Cannot handle a 'searchValue' without any searchPath to search in! (Table: %s, Value: '%s')",
                        name,
                        searchValue));
            }
        }

        /**
         * Counts the number of matches for the given query.
         *
         * @return the number of matching rows
         */
        public long count() {
            return execute(Limit.singleItem()).stream().findFirst().map(row -> row.at(0).asLong(0)).orElse(0L);
        }

        private List<Values> execute(Limit limit, String... pathsToQuery) {
            checkConstraints();

            if (Strings.isEmpty(searchValue)) {
                if (Strings.isFilled(searchPaths)) {
                    return Collections.emptyList();
                }
                if (Strings.isFilled(primaryLang)) {
                    return iscan(primaryLang, fallbackLang, limit, pathsToQuery);
                } else {
                    return scan(limit, pathsToQuery);
                }
            } else if (exact) {
                if (Strings.isFilled(primaryLang)) {
                    return iquery(primaryLang, fallbackLang, searchPaths, searchValue, limit, pathsToQuery);
                } else {
                    return query(searchPaths, searchValue, limit, pathsToQuery);
                }
            } else {
                if (Strings.isFilled(primaryLang)) {
                    return isearch(primaryLang, fallbackLang, searchPaths, searchValue, limit, pathsToQuery);
                } else {
                    return search(searchPaths, searchValue, limit, pathsToQuery);
                }
            }
        }

        /**
         * Attempts to fetch multiple rows matching the given query.
         * <p>
         * In contrast to {@link #allRows(String...)}, this can yield an optimized query if less than
         * {@link #FETCH_PAGE_SIZE} rows are fetched (which should be quite common).
         *
         * @param limit        the number of rows to fetch
         * @param pathsToQuery the paths to return
         * @return a stream which iterates over all selected rows
         */
        public Stream<Values> manyRows(Limit limit, String... pathsToQuery) {
            if (limit.getMaxItems() > 0 && limit.getMaxItems() < FETCH_PAGE_SIZE) {
                return execute(limit, pathsToQuery).stream();
            } else {
                return StreamSupport.stream(new QuerySpliterator(pathsToQuery, this, limit.getItemsToSkip()), false);
            }
        }

        /**
         * Fetches all rows matching the given query.
         * <p>
         * For a very large result set, this will automatically issue multiple fetch queries, using proper
         * pagination.
         *
         * @param pathsToQuery the paths to return
         * @return a stream which iterates over all selected rows
         */
        public Stream<Values> allRows(String... pathsToQuery) {
            return StreamSupport.stream(new QuerySpliterator(pathsToQuery, this, 0), false);
        }

        @Override
        public String toString() {
            return "Query against: " + IDBTable.this;
        }
    }

    private class QuerySpliterator extends PullBasedSpliterator<Values> {

        private final String[] pathsToQuery;
        private final QueryBuilder builder;
        private int currentSkip;

        QuerySpliterator(String[] pathsToQuery, QueryBuilder builder, int currentSkip) {
            this.pathsToQuery = pathsToQuery;
            this.builder = builder;
            this.currentSkip = currentSkip;
        }

        @Override
        protected Iterator<Values> pullNextBlock() {
            if (currentSkip < 0) {
                return null;
            }
            List<Values> nextPage = builder.execute(new Limit(currentSkip, FETCH_PAGE_SIZE), pathsToQuery);
            if (nextPage.isEmpty()) {
                return null;
            }

            if (nextPage.size() >= FETCH_PAGE_SIZE) {
                currentSkip += nextPage.size();
            } else {
                currentSkip = -1;
            }
            return nextPage.iterator();
        }

        @Override
        public int characteristics() {
            return Spliterator.NONNULL | Spliterator.IMMUTABLE;
        }

        @Override
        public String toString() {
            return "Query Result in: " + IDBTable.this;
        }
    }

    /**
     * Creates a query using a fluent builder API.
     *
     * @return the builder used to create the query
     */
    public QueryBuilder query() {
        return new QueryBuilder();
    }

    /**
     * Issues an <tt>IDB.LOOKUP</tt> command.
     * <p>
     * Note that building a query via {@link QueryBuilder} by calling {@link #query()} is most probably more convenient.
     *
     * @param lookupPath   the field(s) to search in
     * @param filterValue  the value to search for
     * @param pathsToQuery the paths to return
     * @return the first row which matched the given query
     */
    public Optional<Values> lookup(String lookupPath, String filterValue, String... pathsToQuery) {
        return jupiter.query(() -> CMD_LOOKUP + " " + name, redis -> {
            String[] args = new String[pathsToQuery.length + 3];
            args[0] = name;
            args[1] = lookupPath;
            args[2] = filterValue;
            System.arraycopy(pathsToQuery, 0, args, 3, pathsToQuery.length);
            redis.sendCommand(CMD_LOOKUP, args);

            return redis.getObjectMultiBulkReply().stream().map(this::parseRow).findFirst();
        });
    }

    @SuppressWarnings("unchecked")
    private Values parseRow(Object obj) {
        if (obj instanceof List) {
            return Values.of(((List<Object>) obj).stream().map(Jupiter::read).toList());
        } else {
            return Values.of(Collections.emptyList());
        }
    }

    /**
     * Issues an <tt>IDB.ILOOKUP</tt> command.
     * <p>
     * Note that building a query via {@link QueryBuilder} by calling {@link #query()} is most probably more convenient.
     *
     * @param mainLanguage     the main language used for translations
     * @param fallbackLanguage the fallback language used for translations
     * @param lookupPath       the field(s) to search in
     * @param filterValue      the value to search for
     * @param pathsToQuery     the paths to return
     * @return the first row which matched the given query
     */
    public Optional<Values> ilookup(String mainLanguage,
                                    String fallbackLanguage,
                                    String lookupPath,
                                    String filterValue,
                                    String... pathsToQuery) {
        return jupiter.query(() -> CMD_ILOOKUP + " " + name, redis -> {
            String[] args = new String[pathsToQuery.length + 5];
            args[0] = name;
            args[1] = mainLanguage;
            args[2] = fallbackLanguage;
            args[3] = lookupPath;
            args[4] = filterValue;
            System.arraycopy(pathsToQuery, 0, args, 5, pathsToQuery.length);
            redis.sendCommand(CMD_ILOOKUP, args);

            return redis.getObjectMultiBulkReply().stream().map(this::parseRow).findFirst();
        });
    }

    /**
     * Issues an <tt>IDB.QUERY</tt> command.
     * <p>
     * Note that building a query via {@link QueryBuilder} by calling {@link #query()} is most probably more convenient.
     *
     * @param lookupPath   the field(s) to search in
     * @param filterValue  the value to search for
     * @param limit        selects which / how many rows to return
     * @param pathsToQuery the paths to return
     * @return the rows which matched the given query
     */
    public List<Values> query(String lookupPath, String filterValue, Limit limit, String... pathsToQuery) {
        return jupiter.query(() -> CMD_QUERY + " " + name, redis -> {
            String[] args = new String[pathsToQuery.length + 5];
            args[0] = name;
            args[1] = String.valueOf(limit.getItemsToSkip());
            args[2] = String.valueOf(limit.getMaxItems());
            args[3] = lookupPath;
            args[4] = filterValue;
            System.arraycopy(pathsToQuery, 0, args, 5, pathsToQuery.length);
            redis.sendCommand(CMD_QUERY, args);

            return parseQueryResult(redis);
        });
    }

    private List<Values> parseQueryResult(Connection redis) {
        Object result = redis.getOne();
        if (result instanceof List) {
            return ((List<?>) result).stream().map(this::parseRow).toList();
        } else {
            return Collections.singletonList(Values.of(new Object[]{result}));
        }
    }

    /**
     * Issues an <tt>IDB.IQUERY</tt> command.
     * <p>
     * Note that building a query via {@link QueryBuilder} by calling {@link #query()} is most probably more convenient.
     *
     * @param mainLanguage     the main language used for translations
     * @param fallbackLanguage the fallback language used for translations
     * @param lookupPath       the field(s) to search in
     * @param filterValue      the value to search for
     * @param limit            selects which / how many rows to return
     * @param pathsToQuery     the paths to return
     * @return the rows which matched the given query
     */
    public List<Values> iquery(String mainLanguage,
                               String fallbackLanguage,
                               String lookupPath,
                               String filterValue,
                               Limit limit,
                               String... pathsToQuery) {
        return jupiter.query(() -> CMD_IQUERY + " " + name, redis -> {
            String[] args = new String[pathsToQuery.length + 7];
            args[0] = name;
            args[1] = mainLanguage;
            args[2] = fallbackLanguage;
            args[3] = String.valueOf(limit.getItemsToSkip());
            args[4] = String.valueOf(limit.getMaxItems());
            args[5] = lookupPath;
            args[6] = filterValue;
            System.arraycopy(pathsToQuery, 0, args, 7, pathsToQuery.length);
            redis.sendCommand(CMD_IQUERY, args);

            return parseQueryResult(redis);
        });
    }

    /**
     * Issues an <tt>IDB.SEARCH</tt> command.
     * <p>
     * Note that building a query via {@link QueryBuilder} by calling {@link #query()} is most probably more convenient.
     *
     * @param lookupPath   the field(s) to search in
     * @param filterValue  the value to search for
     * @param limit        selects which / how many rows to return
     * @param pathsToQuery the paths to return
     * @return the rows which matched the given query
     */
    public List<Values> search(String lookupPath, String filterValue, Limit limit, String... pathsToQuery) {
        return jupiter.query(() -> CMD_SEARCH + " " + name, redis -> {
            String[] args = new String[pathsToQuery.length + 5];
            args[0] = name;
            args[1] = String.valueOf(limit.getItemsToSkip());
            args[2] = String.valueOf(limit.getMaxItems());
            args[3] = lookupPath;
            args[4] = filterValue;
            System.arraycopy(pathsToQuery, 0, args, 5, pathsToQuery.length);
            redis.sendCommand(CMD_SEARCH, args);

            return parseQueryResult(redis);
        });
    }

    /**
     * Issues an <tt>IDB.ISEARCH</tt> command.
     * <p>
     * Note that building a query via {@link QueryBuilder} by calling {@link #query()} is most probably more convenient.
     *
     * @param mainLanguage     the main language used for translations
     * @param fallbackLanguage the fallback language used for translations
     * @param lookupPath       the field(s) to search in
     * @param filterValue      the value to search for
     * @param limit            selects which / how many rows to return
     * @param pathsToQuery     the paths to return
     * @return the rows which matched the given query
     */
    public List<Values> isearch(String mainLanguage,
                                String fallbackLanguage,
                                String lookupPath,
                                String filterValue,
                                Limit limit,
                                String... pathsToQuery) {
        return jupiter.query(() -> CMD_ISEARCH + " " + name, redis -> {
            String[] args = new String[pathsToQuery.length + 7];
            args[0] = name;
            args[1] = mainLanguage;
            args[2] = fallbackLanguage;
            args[3] = String.valueOf(limit.getItemsToSkip());
            args[4] = String.valueOf(limit.getMaxItems());
            args[5] = lookupPath;
            args[6] = filterValue;
            System.arraycopy(pathsToQuery, 0, args, 7, pathsToQuery.length);
            redis.sendCommand(CMD_ISEARCH, args);

            return parseQueryResult(redis);
        });
    }

    /**
     * Issues an <tt>IDB.SCAN</tt> command.
     * <p>
     * Note that building a query via {@link QueryBuilder} by calling {@link #query()} is most probably more convenient.
     *
     * @param limit        selects which / how many rows to return
     * @param pathsToQuery the paths to return
     * @return the rows which matched the given query
     */
    public List<Values> scan(Limit limit, String... pathsToQuery) {
        return jupiter.query(() -> CMD_SCAN + " " + name, redis -> {
            String[] args = new String[pathsToQuery.length + 3];
            args[0] = name;
            args[1] = String.valueOf(limit.getItemsToSkip());
            args[2] = String.valueOf(limit.getMaxItems());
            System.arraycopy(pathsToQuery, 0, args, 3, pathsToQuery.length);
            redis.sendCommand(CMD_SCAN, args);

            return parseQueryResult(redis);
        });
    }

    /**
     * Issues an <tt>IDB.ISCAN</tt> command.
     * <p>
     * Note that building a query via {@link QueryBuilder} by calling {@link #query()} is most probably more convenient.
     *
     * @param mainLanguage     the main language used for translations
     * @param fallbackLanguage the fallback language used for translations
     * @param limit            selects which / how many rows to return
     * @param pathsToQuery     the paths to return
     * @return the rows which matched the given query
     */
    public List<Values> iscan(String mainLanguage, String fallbackLanguage, Limit limit, String... pathsToQuery) {
        return jupiter.query(() -> CMD_ISCAN + " " + name, redis -> {
            String[] args = new String[pathsToQuery.length + 5];
            args[0] = name;
            args[1] = mainLanguage;
            args[2] = fallbackLanguage;
            args[3] = String.valueOf(limit.getItemsToSkip());
            args[4] = String.valueOf(limit.getMaxItems());
            System.arraycopy(pathsToQuery, 0, args, 5, pathsToQuery.length);
            redis.sendCommand(CMD_ISCAN, args);

            return parseQueryResult(redis);
        });
    }

    /**
     * Counts the number of entries in the table.
     *
     * @return the number of entries in this table
     */
    public int size() {
        return jupiter.query(() -> CMD_LEN + " " + name, redis -> {
            redis.sendCommand(CMD_LEN, name);

            return Value.of(redis.getIntegerReply()).asInt(0);
        });
    }

    public String getName() {
        return name;
    }

    @Override
    public String toString() {
        return "IDB Table: " + name + " in: " + jupiter;
    }
}
