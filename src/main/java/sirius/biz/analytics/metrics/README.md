# Metrics Computation and Storage

Provides a computation and storage framework for aggregated metrics.
In most systems, metrics will be initially recorded as [events](../events).
Using the underlying time-series database **Clickhouse** this can be efficiently queried
and used for intense computations.

It is still advisable to compute and store some aggregations (number of X per month per entity)
so that they can be readily displayed in dashboards without much computation.

## Storage

This framework therefore provides a database independent storage called [Metrics](Metrics.java)
which has an implementation for [JDBC](jdbc/SQLMetrics.java) and [MongoDB](mongo/MongoMetrics.java).

One of these frameworks (**biz.analytics-metrics-jdbc** or **biz.analytics-metrics-mongo**) has to be enabled.

## Computation

To compute (aggregate) metrics for entities, [DailyMetricComputer](DailyMetricComputer.java) or [MonthlyMetricComputer](MonthlyMetricComputer.java)
can be subclassed. Note that monthly metric computers are scheduled in a *best effort* manner
on a daily basis for the current month so that these aggregations are also available if possible.

Additionally, global metrics can be computed by subclassing [DailyGlobalMetricComputer](DailyGlobalMetricComputer.java)
or [MonthlyGlobalMetricComputer](MonthlyGlobalMetricComputer.java). 

Note that these schedulers are managed by the scheduling system found in the [scheduler](../scheduler/) package.

