/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.analytics.events;

import sirius.biz.analytics.reports.Cell;
import sirius.biz.analytics.reports.Report;
import sirius.biz.jobs.JobCategory;
import sirius.biz.jobs.JobFactory;
import sirius.biz.jobs.interactive.ReportJobFactory;
import sirius.biz.jobs.params.Parameter;
import sirius.biz.tenants.TenantUserManager;
import sirius.db.jdbc.Row;
import sirius.db.jdbc.SQLQuery;
import sirius.kernel.commons.Amount;
import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.Register;
import sirius.kernel.nls.NLS;
import sirius.web.security.Permission;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Provides a report which shows the utilization of the Clickhouse database which stores all recorded events.
 */
@Register(classes = JobFactory.class)
@Permission(TenantUserManager.PERMISSION_SYSTEM_ADMINISTRATOR)
public class ClickhousePartsReportJobFactory extends ReportJobFactory {

    private static final String COLUMN_DATABASE = "database";
    private static final String COLUMN_TABLE = "table";
    private static final String COLUMN_ROWS = "rows";
    private static final String COLUMN_SIZE = "size";
    private static final String COLUMN_DISK_SIZE = "diskSize";
    private static final String COLUMN_PARTITIONS = "partitions";

    @Part
    private EventRecorder recorder;


    @Override
    public String getLabel() {
        return "Clickhouse Utilization";
    }

    @Nullable
    @Override
    public String getDescription() {
        return "Shows the size of the analytics data warehouse stored in Clickhouse.";
    }

    @Override
    protected void computeReport(Map<String, String> context,
                                 Report report,
                                 BiConsumer<String, Cell> additionalMetricConsumer) throws Exception {
        SQLQuery query = recorder.getDatabase()
                                 .createQuery("SELECT database,"
                                              + " table,"
                                              + " sum(rows) as rows,"
                                              + " sum(data_uncompressed_bytes) size,"
                                              + " sum(bytes_on_disk) diskSize,"
                                              + " count(*) as partitions"
                                              + " FROM system.parts"
                                              + " WHERE database <> 'system'"
                                              + " GROUP BY database, table"
                                              + " ORDER BY database, table");

        report.addColumn(COLUMN_DATABASE, "Database");
        report.addColumn(COLUMN_TABLE, "Table");
        report.addColumn(COLUMN_ROWS, "Rows");
        report.addColumn(COLUMN_SIZE, "Size");
        report.addColumn(COLUMN_DISK_SIZE, "Size on Disk");
        report.addColumn(COLUMN_PARTITIONS, "Partitions");

        Amount numberOfTables = Amount.ZERO;
        Amount totalRows = Amount.ZERO;
        Amount totalSize = Amount.ZERO;
        Amount totalDiskSize = Amount.ZERO;
        Amount numberOfPartitions = Amount.ZERO;

        for (Row row : query.queryList()) {
            report.addCells(cells.of(row.getValue(COLUMN_DATABASE).get()),
                            cells.of(row.getValue(COLUMN_TABLE).get()),
                            cells.rightAligned(row.getValue(COLUMN_ROWS).getAmount()),
                            cells.rightAligned(NLS.formatSize(row.getValue(COLUMN_SIZE).asLong(0))),
                            cells.rightAligned(NLS.formatSize(row.getValue(COLUMN_DISK_SIZE).asLong(0))),
                            cells.rightAligned(row.getValue(COLUMN_PARTITIONS).getAmount()));

            numberOfTables = numberOfTables.add(Amount.ONE);
            totalRows = totalRows.add(row.getValue(COLUMN_ROWS).getAmount());
            totalSize = totalSize.add(row.getValue(COLUMN_SIZE).getAmount());
            totalDiskSize = totalDiskSize.add(row.getValue(COLUMN_DISK_SIZE).getAmount());
            numberOfPartitions = numberOfPartitions.add(row.getValue(COLUMN_PARTITIONS).getAmount());
        }

        additionalMetricConsumer.accept("Num. Tables", cells.of(numberOfTables));
        additionalMetricConsumer.accept("Total Rows", cells.of(totalRows));
        additionalMetricConsumer.accept("Total Size", cells.of(NLS.formatSize(totalSize.getAmount().longValue())));
        additionalMetricConsumer.accept("Total Disk Size",
                                        cells.of(NLS.formatSize(totalDiskSize.getAmount().longValue())));
        additionalMetricConsumer.accept("Num. Partitions", cells.of(numberOfPartitions));
    }

    @Override
    protected void collectParameters(Consumer<Parameter<?, ?>> parameterCollector) {
        // there are no parameters for this job...
    }

    @Override
    public String getCategory() {
        return JobCategory.CATEGORY_MISC;
    }

    @Nonnull
    @Override
    public String getName() {
        return "clickhouse-parts";
    }
}
