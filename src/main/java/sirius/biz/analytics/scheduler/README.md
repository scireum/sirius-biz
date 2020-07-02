# Analytics Scheduling System

This framework is responsible for executing 
[analytical tasks](AnalyticalTask.java) on all kinds of entities. To 
distribute the execution across a cluster of machines and to manage the execution of
these tasks the [distributed tasks framework](../../cluster/work) is used.

To maintain high efficiency, entities for which tasks are to be executed, are grouped
into batches which are then managed as single distributed task. The creation of
appropriate batches is performed by [schedulers](AnalyticsScheduler.java). There
are two base implementations readily available, one for [JDBC](SQLAnalyticalTaskScheduler.java)-
and one for [MongoDB](MongoAnalyticalTaskScheduler.java) entities.

Schedulers can be one of two different flavors: **Guaranteed execution** or **best effort
execution**. The former will always be executed in their desired interval where the latter
will only be scheduled for execution if the underlying job queue (*analytics-best-effort*)
is empty. An example using this execution model would be monthly computed metrics. For this
scenario we'd have **guaranteed executing scheduler** which performs the execution once
per month and an additional **best effort** one which tries to execute the computations each
day in the current month, so that the relected values are up to date (as long as the system 
isn't overloaded).

For maintenance and troubleshooting some options are available:
* The [AnalyticsCommand](AnalyticsCommand.java) can be used in the [System Console](https://localhost:9000/system/console)
by calling **analytics**. This lists all active schedulers, their queue and last execution. It also permits
to forcefully start a scheduler immediately.
* The Logger **analytics** can be set to FINE (using the **logger** command) to observe what the framework is
doing.
* The [Cluster State](https://localhost:9000/system/cluster) and the [Load Info](https://localhost:9000/system/load)
report the queue lengths in which the tasks are scheduler and also list which server is in charge of executing
the tasks.

Examples on how to use this framework can be found in [Checks](../checks) and [Metrics](../metrics).
