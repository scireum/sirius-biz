# Report Helpers

Provides some helper classes for creating and displaying a table / report based on
in-memory data.

Use the [Report-Tag](../../../../../resources/default/taglib/w/report.html.pasta) 
to display a [Report](Report.java). Note that [SimpleReportBatchJobFactory](../../jobs/interactive/ReportJobFactory.java)
can be used to provide a simple report which can be started by a user. Note that also
batch jobs (which cannot be shown interactively as they take too long to compute) can utilize
most of the capabilities by providing a [TableOutput](../../process/output/TableOutput.java)
which also uses [Cells](Cells.java). A simple way of creating such a batch job is to extend
 [SimpleReportBatchJobFactory](../../jobs/batch/SimpleReportBatchJobFactory.java) or
 (if the task is more complex) [ReportBatchProcessFactory](../../jobs/batch/ReportBatchProcessFactory.java).
