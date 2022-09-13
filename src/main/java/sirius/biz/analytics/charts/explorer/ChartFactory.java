/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.analytics.charts.explorer;

import sirius.kernel.di.GlobalContext;
import sirius.kernel.di.std.AutoRegister;
import sirius.kernel.di.std.Named;
import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.Priorized;
import sirius.kernel.nls.NLS;
import sirius.web.security.Permissions;
import sirius.web.security.UserContext;
import sirius.web.services.JSONStructuredOutput;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

/**
 * Provides the base class for all charts which can be shown in the {@link DataExplorerController Data-Explorer}.
 * <p>
 * Subclasses have to be {@link sirius.kernel.di.std.Register registered} in order to be visible to the framework.
 * Note, however, that {@link TimeSeriesChartFactory} provides a base class for all time-series related data, so this
 * might be a better foundation.
 * <p>
 * Note that access control can be either handled by overwriting {@link #isAccessibleToCurrentUser()} or by
 * placing {@link sirius.web.security.Permission} annotations on this class.
 *
 * @param <O> the type of entities for which a chart can be shown
 */
@AutoRegister
public abstract class ChartFactory<O> implements Named, Priorized {

    private static final String[] EMPTY_STRING_ARRAY = new String[0];
    private static final String OUTPUT_LABEL = "label";
    private static final String OUTPUT_SUB_LABEL = "subLabel";
    private static final String OUTPUT_DESCRIPTION = "description";
    private static final String OUTPUT_HINTS = "hints";
    private static final String OUTPUT_HINT = "hint";
    private static final String OUTPUT_REFERENCES = "references";
    private static final String OUTPUT_REFERENCE = "reference";
    private static final String OUTPUT_IDENTIFIER = "identifier";

    @Part
    private GlobalContext globalContext;

    private ChartObjectResolver<O> resolver;
    private List<ChartFactory<O>> referencedProviders;

    /**
     * Specifies the resolver used to suggest and resolve objects for which this chart is provided.
     *
     * @return the resolver to use or <tt>null</tt> if this chart does not depend on a data object
     */
    @Nullable
    protected abstract Class<? extends ChartObjectResolver<O>> getResolver();

    protected ChartObjectResolver<O> resolver() {
        if (resolver == null && getResolver() != null) {
            resolver = globalContext.getPartByType(ChartObjectResolver.class, getResolver());
        }

        return resolver;
    }

    /**
     * Determines if the chart uses a resolver or not.
     *
     * @return <tt>true</tt> if a resolver is used, <tt>false</tt> otherwise
     */
    public boolean hasResolver() {
        return getResolver() != null;
    }

    /**
     * Determines if the chart is accessible by the current user.
     * <p>
     * The default implementation evaluates all {@link sirius.web.security.Permission} annotations present on
     * the class-level.
     *
     * @return <tt>true</tt> if the current user can access a chart, <tt>false</tt> otherwise
     */
    public boolean isAccessibleToCurrentUser() {
        return UserContext.getCurrentUser()
                          .hasPermissions(Permissions.computePermissionsFromAnnotations(getClass())
                                                     .toArray(EMPTY_STRING_ARRAY));
    }

    /**
     * Determines the name of the category to put this chart in.
     * <p>
     * Most probably this should use a constant (like one in {@link sirius.biz.jobs.StandardCategories} for SIRIUS or
     * foundation classes).
     *
     * @return the name of the category to which this chart belongs
     */
    public abstract String getCategory();

    /**
     * Returns the CSS class which specifies the icon to show.
     *
     * @return the CSS class (i.e. fontawesome) to determine the icon to use
     */
    public abstract String getIcon();

    /**
     * Returns the label to show in the overview UI.
     *
     * @return the label to show for this chart. By default, this will fetch the property <tt>ClassName.label</tt>
     */
    public String getLabel() {
        return NLS.get(getClass().getSimpleName() + ".label");
    }

    /**
     * Returns the description to show in the overview UI.
     *
     * @return the description to show for this chart. By default, this will fetch the property <tt>ClassName.description</tt>
     */
    public String getDescription() {
        return NLS.getIfExists(getClass().getSimpleName() + ".description", null).orElse("");
    }

    /**
     * Generates an identifier which can be passed to the {@link DataExplorerController}.
     * <p>
     * This is used by the <tt>t:charts</tt> tag to display appropriate charts next to a data object.
     *
     * @param uri          the uri of the current page (which contains the <tt>t:charts</tt> tag
     * @param targetObject the optional target object which is being shown / processed / edited by the page
     * @return an identifier which can be passed to <tt>/data-explorer</tt> or <tt>null</tt> to indicate that the given
     * object isn't an appropriate parameter.
     */
    @SuppressWarnings("unchecked")
    @Nullable
    public String generateIdentifier(@Nonnull String uri, @Nullable Object targetObject) {
        if (resolver() == null || targetObject == null) {
            if (isMatchingChart(uri, targetObject)) {
                return getName();
            } else {
                return null;
            }
        } else if (resolver().getTargetType().isAssignableFrom(targetObject.getClass())) {
            return getName() + ":" + resolver().fetchIdentifier((O) targetObject);
        } else {
            return null;
        }
    }

    /**
     * Determines if the given URI or target object are plausible parameters to launch this global chart (without a
     * resolver).
     *
     * @param uri          the uri of the current page (which contains the <tt>t:charts</tt> tag
     * @param targetObject the optional target object which is being shown / processed / edited by the page
     * @return <tt>true</tt> if the chart is to be shown for the given uri and or target, <tt>false</tt> otherwise
     */
    protected boolean isMatchingChart(String uri, Object targetObject) {
        return false;
    }

    /**
     * Determines the label to show above the chart.
     *
     * @param object the currently selected object for the chart
     * @return the label to show above the chart. By default, this is {@link #getLabel()}.
     */
    protected String getChartLabel(@Nullable O object) {
        return getLabel();
    }

    /**
     * Determines the sub-label to show above the chart.
     *
     * @param object the currently selected object for the chart
     * @return the sub-label to show. By default, this will show the name of the selected object or "" if none is
     * selected/required.
     */
    protected String getChartSubLabel(@Nullable O object) {
        return object == null ? "" : object.toString();
    }

    /**
     * Determines the description to show next to the chart.
     *
     * @param object the currently selected object for the chart
     * @return the label to show next the chart. By default, this will fetch the property <tt>ClassName.description</tt>
     * and fall back to {@link #getDescription()}.
     */
    protected String getChartDescription(@Nullable O object) {
        return NLS.getIfExists(getClass().getSimpleName() + ".chartDescription", null).orElseGet(this::getDescription);
    }

    /**
     * Collects a list of similar / matching charts to recommend.
     *
     * @param referenceChartConsumer a consumer to be supplied with chart classes to suggest to the user
     */

    protected abstract void collectReferencedCharts(Consumer<Class<? extends ChartFactory<O>>> referenceChartConsumer);

    /**
     * Computes the effectively visible referenced providers.
     *
     * @return a list of recommended {@link ChartFactory factories} which are known to be visible to the user.
     */
    protected List<ChartFactory<O>> fetchReferencedFactories() {
        if (referencedProviders == null) {
            List<ChartFactory<O>> providers = new ArrayList<>();
            collectReferencedCharts(providerType -> {
                ChartFactory<O> provider = globalContext.getPartByType(ChartFactory.class, providerType);
                if (provider != null && provider.isAccessibleToCurrentUser()) {
                    providers.add(provider);
                }
            });

            referencedProviders = providers;
        }

        return Collections.unmodifiableList(referencedProviders);
    }

    /**
     * Provides the main entrance point used by {@link DataExplorerController} to actually compute the output JSON for
     * this chart.
     *
     * @param object           the selected reference object
     * @param start            the start of the selected period
     * @param end              the end of the selected period
     * @param granularity      the granularity to provide
     * @param comparisonPeriod the selected type of comparison period to provide
     * @param output           the output to write all JSON to
     * @throws Exception in case of any error when computing the chart
     */
    protected void generateOutput(@Nullable O object,
                                  LocalDate start,
                                  LocalDate end,
                                  Granularity granularity,
                                  ComparisonPeriod comparisonPeriod,
                                  JSONStructuredOutput output) throws Exception {
        List<String> hints = new ArrayList<>();
        output.property(OUTPUT_LABEL, getChartLabel(object));
        output.property(OUTPUT_SUB_LABEL, getChartSubLabel(object));
        output.property(OUTPUT_DESCRIPTION, getChartDescription(object));
        outputReferences(object, output);

        computeData(object, start, end, granularity, comparisonPeriod, hints::add, output);

        output.array(OUTPUT_HINTS, OUTPUT_HINT, hints);
    }

    /**
     * Outputs all visible references.
     *
     * @param object the selected reference object
     * @param output the output to write all JSON to
     */
    private void outputReferences(O object, JSONStructuredOutput output) {
        output.beginArray(OUTPUT_REFERENCES);
        for (ChartFactory<O> referencedProvider : fetchReferencedFactories()) {
            output.beginObject(OUTPUT_REFERENCE);
            output.property(OUTPUT_LABEL, referencedProvider.getLabel());
            if (referencedProvider.resolver() != null) {
                output.property(OUTPUT_IDENTIFIER,
                                referencedProvider.getName() + ":" + referencedProvider.resolver()
                                                                                       .fetchIdentifier(object));
            } else {
                output.property(OUTPUT_IDENTIFIER, referencedProvider.getName());
            }
            output.endObject();
        }
        output.endArray();
    }

    /**
     * Computes the actual chart data and outputs it into the given output.
     *
     * @param object           the selected reference object
     * @param start            the start of the selected period
     * @param end              the end of the selected period
     * @param granularity      the granularity to provide
     * @param comparisonPeriod the selected type of comparison period to provide
     * @param hints            a consumer which can be provided with hints to show for a chart
     * @param output           the output to write all JSON to
     * @throws Exception in case of any error when computing the chart
     */
    protected abstract void computeData(O object,
                                        LocalDate start,
                                        LocalDate end,
                                        Granularity granularity,
                                        ComparisonPeriod comparisonPeriod,
                                        Consumer<String> hints,
                                        JSONStructuredOutput output) throws Exception;
}
