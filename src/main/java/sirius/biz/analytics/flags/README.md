# Flags

## Execution Flags

Provides a framework which stores and retrieves the execution timestamp of analytical tasks for certain reference objects.

One user of this framework is the [AnalyticalEngine](../scheduler/AnalyticalEngine.java) which uses this for
schedulers which are only to be invoked once per month.

This is a database independent framework. Use [ExecutionFlags](ExecutionFlags.java) as main entry point and
enable either **biz.analytics-execution-flags-jdbc** or **biz.analytics-execution-flags-mongo** as framework
depending on the database being used.

## Performance Flags

Provides a framework which stores a set of flags per entity. This can be either an SQLEntity (which would
use [SQLPerformanceData](jdbc/SQLPerformanceData.java)) or a MongoEntity (which would use
[MongoPerformanceData](mongo/MongoPerformanceData.java)). Flags will be stored in a way optimized for the
underlying database. We use bit fields for JDBC/SQL and StringList for MongoDB. Most probably a performance
flag will be toggled when computing metrics e.g. via a **MonthlyMetricComputer**. However, they can also
be toggled on demand using the respective [PerformanceFlagModifier](PerformanceFlagModifier.java).
  
