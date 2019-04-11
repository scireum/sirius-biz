# Analytics Framework

Provides a collection for frameworks for acquiring, aggregating and reporting 
analytical data.

* [Charts](charts)\
Provides helper classes for collecting and transferring chart data to the frontend.
* [Checks](checks)\
Executes daily or change based checks on entities using a distributed background facility.
* [Events](events)\
Records and batch inserts events in a timeseries database for later inspection.
* [Flags](flags)\
Stores and retrieves the execution timestamp of analytical tasks for certain reference objects.
* [Indicators](indicators)\
Executes and stores indicators for entities.
* [Metrics](metrics)\
Provides both, a distributed background facility to compute (aggregate) metrics
on a daily or monthly basis and also a **database independent** storage facility for the computed values.
* [Offheap](offheap)\
Provides data structures which reside outside of the managed heap and can be used in
computations which require a very large amount of RAM.
* [Reports](reports)\
Provides a set of helper classes to create and render HTML tables (with elaborate cell stylings).
* [Scheduler](scheduler)\
In charge of actually distributing and executing all analytical tasks (checks, computations etc.).
