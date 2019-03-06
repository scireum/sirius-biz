# Execution Flags

Provides a framework which stores and retrieves the execution timestamp of analytical tasks for certain reference objects.

One user of this framework is the [AnalyticalEngine](../scheduler/AnalyticalEngine.java) which uses this for
schedulers which are only to be invoked once per month.

This is a database independent framework. Use [ExecutionFlags](ExecutionFlags.java) as main entry point and
enable either **biz.analytics.execution-flags-jdbc** or **biz.analytics.execution-flags-mongo** as framework
depending on the database being used.  
