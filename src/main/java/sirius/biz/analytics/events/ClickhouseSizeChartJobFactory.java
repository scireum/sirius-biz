/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.analytics.events;

import sirius.biz.analytics.charts.Dataset;
import sirius.biz.analytics.reports.Cell;
import sirius.biz.jobs.StandardCategories;
import sirius.biz.jobs.interactive.LinearChartJobFactory;
import sirius.biz.jobs.params.EntityDescriptorParameter;
import sirius.biz.jobs.params.Parameter;
import sirius.biz.tenants.TenantUserManager;
import sirius.db.jdbc.Row;
import sirius.db.jdbc.SQLQuery;
import sirius.db.mixing.EntityDescriptor;
import sirius.kernel.commons.Amount;
import sirius.kernel.commons.Strings;
import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.Register;
import sirius.kernel.nls.NLS;
import sirius.web.security.Permission;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Shows the growth of the analytics data warehouse stored in Clickhouse.
 */
@Register
@Permission(TenantUserManager.PERMISSION_SYSTEM_ADMINISTRATOR)
public class ClickhouseSizeChartJobFactory extends LinearChartJobFactory {

    /**
     * Contains the job name as constant to be referenced by the {@link ClickhousePartsReportJobFactory}.
     */
    public static final String JOB_NAME = "clickhouse-size";

    @Part
    private EventRecorder recorder;

    private final Parameter<EntityDescriptor> entityDescriptorParameter =
            new EntityDescriptorParameter().withFilter(descriptor -> Event.class.isAssignableFrom(descriptor.getType()))
                                           .build();

    @Override
    public String getLabel() {
        return "Clickhouse Size per Month";
    }

    @Override
    public String getCurrentLabel(Map<String, String> context) {
        return entityDescriptorParameter.get(context)
                                        .map(descriptor -> Strings.apply("Clickhouse size for: %s",
                                                                         descriptor.getType().getSimpleName()))
                                        .orElse(getLabel());
    }

    @Nullable
    @Override
    public String getDescription() {
        return "Shows the monthly size of the analytics data warehouse stored in Clickhouse.";
    }

    @Override
    public String getCurrentDescription(Map<String, String> context) {
        return entityDescriptorParameter.get(context)
                                        .map(descriptor -> Strings.apply("Shows the monthly size of %s",
                                                                         descriptor.getType().getSimpleName()))
                                        .orElse(getDescription());
    }

    @Override
    protected void computeChartData(Map<String, String> context,
                                    Consumer<List<String>> labelConsumer,
                                    Consumer<Dataset> datasetConsumer,
                                    BiConsumer<String, Cell> additionalMetricConsumer) throws Exception {
        SQLQuery query = recorder.getDatabase()
                                 .createQuery("SELECT toYear(max_date) as year,"
                                              + " toMonth(max_date) as month,"
                                              + " sum(rows) as rows,"
                                              + " sum(bytes_on_disk) as diskSize"
                                              + " FROM system.parts"
                                              + " WHERE database <> 'system'"
                                              + " [AND table = ${table}]"
                                              + " GROUP BY toYear(max_date), toMonth(max_date)"
                                              + " ORDER BY toYear(max_date), toMonth(max_date)");

        query.set("table", entityDescriptorParameter.get(context).map(EntityDescriptor::getRelationName).orElse(null));

        List<String> labels = new ArrayList<>();
        Dataset rows = new Dataset("Rows");
        Dataset size = new Dataset("Size (MB)").onAxis("right");

        Amount totalRows = Amount.ZERO;
        Amount totalSize = Amount.ZERO;
        Amount numberOfMonths = Amount.ZERO;

        for (Row row : query.queryList()) {
            labels.add(row.getValue("year").asString() + "/" + row.getValue("month").asString());
            rows.addValue(row.getValue("rows").asLong(0));
            size.addValue(row.getValue("diskSize").asLong(0) / 1024 / 1024);

            totalRows = totalRows.add(row.getValue("rows").getAmount());
            totalSize = totalSize.add(row.getValue("diskSize").getAmount());
            numberOfMonths = numberOfMonths.add(Amount.ONE);
        }

        labelConsumer.accept(labels);
        datasetConsumer.accept(rows);
        datasetConsumer.accept(size);

        additionalMetricConsumer.accept("Num. Months", cells.of(numberOfMonths));
        additionalMetricConsumer.accept("Total Rows", cells.of(totalRows));
        additionalMetricConsumer.accept("Total Size", cells.of(NLS.formatSize(totalSize.getAmount().longValue())));
    }

    @Override
    protected String getTemplate() {
        return BAR_CHART_TEMPLATE;
    }

    @Override
    protected void collectParameters(Consumer<Parameter<?>> parameterCollector) {
        parameterCollector.accept(entityDescriptorParameter);
    }

    @Override
    public String getCategory() {
        return StandardCategories.MONITORING;
    }

    @Nonnull
    @Override
    public String getName() {
        return JOB_NAME;
    }
}
