# Layer 1 - Replication

Provides the mechanics to schedule and execute the replication tasks required to keep
two [ObjectStorageSpace](../ObjectStorageSpace.java) in sync (by replicating one to another).

Note that this system normally runs by itself as soon as it is enabled in the configuration.
As the replication tasks need to be stored in a database, one can either enable the framework
**biz.storage-replication-jdbc** to use a **JDBC datasource** or **biz.storage-replication-mongo**
to store the replication tasks in a **MongoDB**.

The framework uses a background loop ([ReplicationBackgroundLoop](ReplicationBackgroundLoop.java))
to schedule batches of executable replication tasks into a distributed task queue. 
The [distributed tasks framework](../../../cluster/work/) then schedules these batches onto appropriate
worker nodes which perform the copy or delete operations.
