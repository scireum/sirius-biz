/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.web;

import sirius.db.jdbc.Databases;
import sirius.db.mixing.BaseEntity;
import sirius.db.mixing.DateRange;
import sirius.db.mixing.Mapping;
import sirius.db.mixing.query.Query;
import sirius.db.mixing.query.QueryField;
import sirius.db.mixing.query.constraints.Constraint;
import sirius.kernel.cache.ValueComputer;
import sirius.kernel.commons.Strings;
import sirius.kernel.commons.Tuple;
import sirius.kernel.commons.Value;
import sirius.kernel.commons.Watch;
import sirius.kernel.nls.NLS;
import sirius.web.controller.Facet;
import sirius.web.controller.FacetItem;
import sirius.web.controller.Message;
import sirius.web.controller.Page;
import sirius.web.http.WebContext;
import sirius.web.security.UserContext;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Helper class to build a query, bind it to values given in a {@link WebContext} and create a resulting {@link Page}
 * which can be used to render a resulting table and filter box.
 *
 * @param <E> the generic type of the entities being queried
 * @param <Q> the effective type of the generated query
 * @param <C> the type of constraints accepted by the generated query
 * @param <B> recursive definition of the BasePageHelper with generics
 */
public abstract class BasePageHelper<E extends BaseEntity<?>, C extends Constraint, Q extends Query<Q, E, C>, B extends BasePageHelper<E, C, Q, B>> {

    protected static final int DEFAULT_PAGE_SIZE = 25;
    protected WebContext webContext;
    protected Function<String, Value> parameterProvider;
    protected Q baseQuery;
    protected List<QueryField> searchFields = Collections.emptyList();
    protected List<Tuple<Facet, BiConsumer<Facet, Q>>> facets = new ArrayList<>();
    protected int pageSize = DEFAULT_PAGE_SIZE;
    protected boolean fetchTotalCount = false;
    protected boolean debugging;

    protected BasePageHelper(Q query) {
        this.baseQuery = query;
    }

    /**
     * Attaches a web context to the helper, to fetch filter and pagination values from.
     *
     * @param webContext the request to attach
     * @return the helper itself for fluent method calls
     */
    @SuppressWarnings("unchecked")
    public B withContext(WebContext webContext) {
        this.webContext = webContext;
        this.parameterProvider = webContext::get;
        return (B) this;
    }

    /**
     * Defines a custom parameter provider to fetch filter and pagination values from.
     * <p>
     * This can be used instead of supplying a webContext via {@link #withContext(WebContext)}, but if you want to create a page
     * using {@link #asPage()}, a web context is still needed.
     *
     * @param parameterProvider the context where filter values are fetched from
     * @return the helper itself for fluent method calls
     */
    @SuppressWarnings("unchecked")
    public B withParameterProvider(Function<String, Value> parameterProvider) {
        this.parameterProvider = parameterProvider;
        return (B) this;
    }

    /**
     * Specifies one or more search fields which will be searched if a <tt>query</tt>
     * if given in the <tt>WebContext</tt>.
     *
     * @param searchFields the fields to search in
     * @return the helper itself for fluent method calls
     */
    @SuppressWarnings("unchecked")
    public B withSearchFields(QueryField... searchFields) {
        this.searchFields = Arrays.asList(searchFields);
        return (B) this;
    }

    /**
     * Adds a flag, that the total count should also be fetched for the given query.
     * <p>
     * The count is available via {@link Page#getTotal()} after creating the {@link Page}.
     *
     * @return the helper itself for fluent method calls
     */
    @SuppressWarnings("unchecked")
    public B withTotalCount() {
        this.fetchTotalCount = true;
        return (B) this;
    }

    /**
     * Adds a filter facet which will show distinct values of the given property.
     *
     * @param facet the facet to add
     * @return the helper itself for fluent method calls
     */
    public B addFilterFacet(Facet facet) {
        return addFacet(facet, (f, q) -> q.eqIgnoreNull(Mapping.named(f.getName()), f.getValue()));
    }

    /**
     * Adds a filter facet which a custom filter implementation.
     *
     * @param facet  the facet to add
     * @param filter the custom logic which determines how a filter value is applied to the query.
     * @return the helper itself for fluent method calls
     */
    public B addFacet(Facet facet, BiConsumer<Facet, Q> filter) {
        return addFacet(facet, filter, null);
    }

    /**
     * Adds a filter facet with custom filter implementation and a custom item computer.
     *
     * @param facet         the facet to add
     * @param filter        the custom logic which determines how a filter value is applied to the query.
     * @param itemsComputer the custom logic which determines the list of items in the filter
     * @return the helper itself for fluent method calls
     */
    @SuppressWarnings("unchecked")
    public B addFacet(Facet facet, BiConsumer<Facet, Q> filter, BiConsumer<Facet, Q> itemsComputer) {
        Objects.requireNonNull(baseQuery);

        facet.withValue(getParameterValue(facet.getName()).getString());
        filter.accept(facet, baseQuery);
        facets.add(Tuple.create(facet, itemsComputer));

        return (B) this;
    }

    /**
     * Adds a time series based filter which permits to filter on certain time ranges.
     *
     * @param name         the name of the field to filter on
     * @param title        the title of the filter shown to the user
     * @param useLocalDate determines if the filter should be applied as {@link java.time.LocalDate} (<tt>true</tt>)
     *                     or as {@link java.time.LocalDateTime} (<tt>false</tt>). This is crucial, as these
     *                     are entirely differently encoded in the database (see {@link Databases#convertValue(Object)}.
     * @param ranges       the ranges which are supported as filter values
     * @return the helper itself for fluent method calls
     */
    @SuppressWarnings("unchecked")
    public B addTimeFacet(String name, String title, boolean useLocalDate, DateRange... ranges) {
        createTimeFacet(name, title, useLocalDate, ranges);

        return (B) this;
    }

    protected Facet createTimeFacet(String name, String title, boolean useLocalDate, DateRange[] ranges) {
        Facet facet = new Facet(title, name, null, null);
        addFacet(facet, (f, q) -> {
            for (DateRange range : ranges) {
                if (Strings.areEqual(f.getValue(), range.getKey())) {
                    range.applyTo(name, useLocalDate, q);
                }
            }
        });
        for (DateRange range : ranges) {
            facet.addItem(range.getKey(), range.toString(), -1);
        }

        return facet;
    }

    /**
     * Adds a boolean based filter which permits to filter on boolean values.
     *
     * @param name  the name of the field to filter on
     * @param title the title of the filter shown to the user
     * @return the helper itself for fluent method calls
     */
    public B addBooleanFacet(String name, String title) {
        String facetValue = getParameterValue(name).getString();
        Facet facet = new Facet(title, name, facetValue, null);
        facet.addItem("true", NLS.get("NLS.yes"), -1);
        facet.addItem("false", NLS.get("NLS.no"), -1);

        return addFacet(facet, (f, q) -> {
            Value filterValue = Value.of(f.getValue());
            if (filterValue.isFilled()) {
                q.eq(Mapping.named(f.getName()), filterValue.asBoolean());
            }
        });
    }

    /**
     * Adds a facet with all constants of the given enum as filter values.
     * <p>
     * Note that all constants are shown, independent of whether matching values are available or not.
     *
     * @param name     the name of the field to filter an
     * @param title    the title of the filter shown to the user
     * @param enumType the enum used to determine the options (constants)
     * @return the helper itself for fluent method calls
     */
    public B addEnumFacet(String name, String title, Class<? extends Enum<?>> enumType) {
        Facet facet = new Facet(title, name, getParameterValue(name).getString(), null);
        Arrays.stream(enumType.getEnumConstants()).forEach(e -> facet.addItem(e.name(), e.toString(), -1));

        return addFilterFacet(facet);
    }

    /**
     * Adds a new facet which is only shown if the given parameter is present in the current {@link WebContext}.
     * <p>
     * This can be used to show (and keep) a special parameter in a table view. If the parameter isn't present,
     * nothing will happen. If a value is given the parameter is shown as facet and it is also kept by using a
     * facet and an additional hidden facet for its label.
     *
     * @param parameterName      the name of the parameter in the web request
     * @param labelParameterName the name of the parameter which contains the label to show
     * @param filterField        the name of the database field to filter on
     * @param title              the label of the facet
     * @param translator         if no label parameter is present, this translator is used to retrieve one
     * @return the helper itself for fluent method calls
     */
    @SuppressWarnings("unchecked")
    public B addParameterFacet(@Nonnull String parameterName,
                               @Nullable String labelParameterName,
                               @Nonnull Mapping filterField,
                               @Nonnull String title,
                               @Nullable ValueComputer<String, String> translator) {
        String value = getParameterValue(parameterName).getString();

        if (Strings.isFilled(value)) {
            Facet facet = new Facet(title, parameterName, value, translator);
            Value labelAsValue = webContext.get(labelParameterName);
            facet.addItem(value, labelAsValue.getString(), -1);
            addFacet(facet, (f, q) -> q.eqIgnoreNull(filterField, f.getValue()));
            if (labelAsValue.isFilled()) {
                addFacet(new Facet(title, labelParameterName, labelAsValue.asString(), translator),
                         (ignoredFacet, ignoredQuery) -> {
                         });
            }
        }

        return (B) this;
    }

    /**
     * Creates a facet which is used for sorting rather than filtering.
     *
     * @param sortOptions  the list of available sort options
     * @param translator   provides a translation for a search option. By default {@link NLS#smartGet(String)} is used
     * @param sortFunction the function which applies the selected sort option
     * @return the helper itself for fluent method calls
     */
    public B addSortFacet(@Nonnull List<String> sortOptions,
                          @Nullable ValueComputer<String, String> translator,
                          BiConsumer<String, Q> sortFunction) {
        return addFacet(new Facet(NLS.get("BasePageHelper.sort"),
                                  "sort",
                                  null,
                                  translator == null ? NLS::smartGet : translator), (f, q) -> {
            if (f.getValue() != null) {
                sortFunction.accept(f.getValue(), q);
            } else {
                // If no sort option is selected, use the first one...
                sortOptions.stream().findFirst().ifPresent(item -> sortFunction.accept(item, q));
            }
        }, (f, q) -> {
            // Make all sort options visible and mark the first as selected if no other is active
            f.addItems(sortOptions);
            if (f.getValue() == null) {
             f.getItems().stream().findFirst().ifPresent(FacetItem::forceActive);
            }
        });
    }

    /**
     * Adds a sort facet based on the given sort options.
     *
     * @param sortOptions the options to provide
     * @param translator  provides a translation for a search option. By default {@link NLS#smartGet(String)} is used
     * @return the helper itself for fluent method calls
     */
    public B addSortFacet(@Nonnull List<Tuple<String, Consumer<Q>>> sortOptions,
                          @Nullable ValueComputer<String, String> translator) {
        return addSortFacet(Tuple.firsts(sortOptions),
                            translator,
                            (key, query) -> sortOptions.stream()
                                                       .filter(option -> Strings.areEqual(option.getFirst(), key))
                                                       .findFirst()
                                                       .ifPresent(option -> option.getSecond().accept(query)));
    }

    /**
     * Adds a sort facet based on the given sort options.
     *
     * @param sortOptions the options to provide
     * @return the helper itself for fluent method calls
     */
    @SafeVarargs
    public final B addSortFacet(Tuple<String, Consumer<Q>>... sortOptions) {
        return addSortFacet(Arrays.asList(sortOptions), null);
    }

    /**
     * Specifies the number of items shown on the page that gets rendered using this pageHelper.
     *
     * @param pageSize the number of items shown per page
     * @return the helper itself for fluent method calls
     */
    @SuppressWarnings("unchecked")
    public B withPageSize(int pageSize) {
        this.pageSize = pageSize;
        return (B) this;
    }

    /**
     * Wraps the given data into a {@link Page} which can be used to render a table, filterbox and support pagination.
     * <p>
     * This can only be done if a web context has been provided via {@link #withContext(WebContext)}.
     *
     * @return the given data wrapped as <tt>Page</tt>
     */
    public Page<E> asPage() {
        Objects.requireNonNull(webContext);
        Watch w = Watch.start();
        Page<E> result = new Page<E>().withStart(1).withPageSize(pageSize);
        result.bindToRequest(webContext);

        applyQuery(result.getQuery());

        applyFacets(result);

        try {
            setupPaging(result);
            List<E> items = executeQuery();
            enforcePaging(result, items);
            fillPage(w, result, items);

            if (debugging) {
                UserContext.message(Message.info(Strings.apply("Effective Query: %s (Matches: %s, Duration: %s ms)",
                                                               baseQuery,
                                                               baseQuery.count(),
                                                               w.elapsedMillis())));
            }
        } catch (Exception e) {
            UserContext.handle(e);
        }

        return result;
    }

    /**
     * Returns the underlying query for this page helper including all given facets.
     *
     * @return the {@link Query} object
     */
    public Q buildUnderlyingQuery() {
        String query = getParameterValue("query").getString();
        applyQuery(query);

        applyFacets(null);
        return baseQuery;
    }

    private void applyQuery(String query) {
        if (Strings.isFilled(query) && !searchFields.isEmpty()) {
            Tuple<C, Boolean> constraintAndFlag =
                    baseQuery.filters().compileString(baseQuery.getDescriptor(), query, searchFields);
            baseQuery.where(constraintAndFlag.getFirst());
            this.debugging = constraintAndFlag.getSecond();
        }
    }

    protected Value getParameterValue(String name) {
        if (parameterProvider == null) {
            throw new IllegalStateException(
                    "A parameter provider or web context needs to be attached to the page helper first!");
        }
        return parameterProvider.apply(name);
    }

    protected void fillPage(Watch w, Page<E> result, List<E> items) {
        result.withDuration(w.duration());
        result.withItems(items);
    }

    protected void enforcePaging(Page<E> result, List<E> items) {
        int originalSize = items.size();
        if (originalSize > pageSize) {
            result.withHasMore(true);
            items.remove(items.size() - 1);
        }
        if (fetchTotalCount && (originalSize > pageSize || result.getStart() > pageSize)) {
            result.withTotalItems((int) baseQuery.count());
        } else {
            result.withTotalItems(items.size());
        }
    }

    protected List<E> executeQuery() {
        return baseQuery.queryList();
    }

    protected void setupPaging(Page<E> result) {
        baseQuery.skip(result.getStart() - 1);
        baseQuery.limit(pageSize + 1);
    }

    protected void applyFacets(@Nullable Page<E> result) {
        for (Tuple<Facet, BiConsumer<Facet, Q>> f : facets) {
            if (f.getSecond() != null) {
                f.getSecond().accept(f.getFirst(), baseQuery);
            }
            if (result != null) {
                result.addFacet(f.getFirst());
            }
        }
    }

    public Q getBaseQuery() {
        return baseQuery;
    }
}
