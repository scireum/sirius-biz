# Distributed Data Checks

This framework is responsible to executing either [daily](DailyCheck.java) or [on change](ChangeCheck.java) 
checks on JDBC or MongoDB entities. To use this framework simply provide
subclasses of either **DailyCheck** or **ChangeCheck** and *register* them as either of the two.

There are schedulers for JDBC: [daily](SQLDailyCheckScheduler.java) / [on change](SQLChangeCheckScheduler.java)
as well as for MongoDB: [daily](MongoDailyCheckScheduler.java) / [on change](MongoChangeCheckScheduler.java).
Note that all these schedulers execute in the same queue and are also **best effort**
schedulers so daily execution is not guaranteed if the system is overloaded.

An example of daily checks would be the [Checkups Framework](../../retention/checkups). 
[Checkup](../../retention/checkups/Checkup.java) itself is a daily check.

An implementation of change checks can be found in the [Indicators Framework](../indicators).
[BatchIndicatorCheck](../indicators/BatchIndicatorCheck.java) is a change check which executes all *batched* indicators or an entity.
