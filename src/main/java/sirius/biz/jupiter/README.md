# Jupiter connectivity

Permits connecting to one or more [Jupiter](https://github.com/scireum/jupiter)
instances.

## Configuration

As many Jupiter tasks are "stateless" / can failover gracefully to
another node, one can specify a fallback instance per connection.

As Jupiter itself speaks the Redis RESP protocol, each instance has
to be defined as Redis pool in **redis.pools.NAME**. By default, we
will use **redis.pools.jupiter**.

See **component-biz.conf** for further settings.

In order to transfer data from the system configuration into Jupiter,
a [JupiterConfigUpdater](JupiterConfigUpdater.java) can be defined,
e.g. [LRUCacheConfigUpdater](LRUCacheConfigUpdater.java) or
[RepositoryConfigUpdater](RepositoryConfigUpdater.java).

## Repositories

Jupiter can consume files to be stored in its repository. These files are
then internally processed e.g. to provider InfoGraphDB tables or sets. In
order to provide data to Jupiter, one can specify a S3 object store which
may contain shared master data to be loaded into Jupiter.

Also, a local repository can be defined as storage space. This can then be
filled manually or be updated regularly via 
[JupiterDataProviders](JupiterDataProvider.java).

## Synchronization

By default, if **automaticUpdate** is true, the configuration to use as well
as all repository contents is update upon system start. Also these infos
as well as the **JupiterDataProviders** itself are invoked every night so
that Jupiter stays up to date. This is handled via [JupiterSync](JupiterSync.java).
Finally this synchronization can be forced to run via the [JupiterSyncJob](JupiterSyncJob.java)

# Modules

* The LRU cache within Jupiter can be accessed via `Jupiter.getDefault().lru()`.
  Using the [LRUCache](LRUCache.java), all commands can be executed.
* InfoGraphDB can be accessed via `Jupiter.getDefault().idb()`.
  Using the [InfoGraphDB](InfoGraphDB.java), all specific tables and sets
  can be accessed.
* Using `Jupiter.getDefault().repository()` the [Repository](Repository.java)
  can be accessed manually.
