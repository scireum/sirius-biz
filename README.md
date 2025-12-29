# sirius-biz
![sirius](https://raw.githubusercontent.com/scireum/sirius-kernel/main/docs/sirius.jpg)
[![Build Status](https://drone.scireum.com/api/badges/scireum/sirius-biz/status.svg?ref=refs/heads/main)](https://drone.scireum.com/scireum/sirius-biz)

Welcome to the **business module** of the SIRIUS OpenSource framework created by [scireum GmbH](https://www.scireum.de). 
To learn more about what SIRIUS is please refer to documentation of the [kernel module](https://github.com/scireum/sirius-kernel).

# SIRIUS Business Module

Provides a foundation for creating web based SaaS solutions. This module contains frameworks which permit to create
rock solid business applications that can easily be clustered to achieve high availability. Also a lot of common
data objects and frameworks (multi tenancy, code lists, background jobs, storage, data import) are provided.

## Important files of this module: 

* [Default configuration](src/main/resources/component-070-biz.conf)
* [Maven setup](pom.xml)
* [Development Settings](develop.conf)
* [Docker Setup](docker-compose.yml)

## Using sirius-biz

If **sirius-biz** is used in a custom project, the [docker-compose.yml](docker-compose.yml) and [develop.conf](develop.conf)
are a good starting point for getting to pace quickly. Note that you can use the **Setup** class provided by
*sirius-kernel* to start SIRIUS in your IDE locally.

## Default UI

Using the configuration provided above will enable a default user manager (and even create a default user "system" with password "system").
After logging in, you can examine the built-in functions like the 
[Console](http://localhost:9000/system/console), [SQL Debugger](http://localhost:9000/system/sql),
 [SQL Schema Tool](http://localhost:9000/system/schema). You can also inspect the cluster state
 using [Cluster](http://localhost:9000/system/cluster). Note that you can also access the
 tooling provided by [sirius-web](https://github.com/scireum/sirius-web).


## Frameworks

* [HTTP/WebUI Helpers](src/main/java/sirius/biz/web)\
Provides a set of base classes for generating CRUD controllers for **JDBC Databases**, **MongoDB** and **Elasticsearch**.
* [Model Helpers](src/main/java/sirius/biz/model)\
Provides commonly used composites to be embedded in database entities.
* [JDBC Helpers](src/main/java/sirius/biz/jdbc)\
Provides a set of base classes for JDBC entities and a controller to view and update the database schema and to 
directly perform **SQL** queries.
* [MongoDB Helpers](src/main/java/sirius/biz/mongo)\
Provides a set of base entities to be used with **MongoDB**.
* [Elasticsearch Helpers](src/main/java/sirius/biz/elastic)\
Contains base entities for Elasticsearch. Also, index maintenance jobs are provided to manage schema migrations which 
are not supported by Elasticsearch directly.
* [Cluster Management Facility](src/main/java/sirius/biz/cluster)\
Provides an interconnect facility which is used to discover and to communicate with other cluster nodes.
Also provides an orchestration layer for caches, cluster health and distributed cluster tasks.
* [Isenguard](src/main/java/sirius/biz/isenguard)\
Provides a clusterwide firewall and rate limiting facility based on **Redis**.
* [Global Locks](src/main/java/sirius/biz/locks)\
Contains a framework to manage cluster wide locks. Various strategies are provided to best match
the system topology.
* [Analytics Framework](src/main/java/sirius/biz/analytics)\
Provides a collection for frameworks for acquiring, aggregating and reporting 
analytical data.
* [Jobs Frameworks](src/main/java/sirius/biz/jobs)\
Permits to define jobs which can be started by users of the system. These are dispatched and executed via
the [distributed tasks facility](src/main/java/sirius/biz/cluster/work) and can be monitored using
[processes](src/main/java/sirius/biz/process).
* [Process Facility](src/main/java/sirius/biz/process)\
Provides a management and report facility for background activities performed for the system or individual users.
* [Data Import Facility](src/main/java/sirius/biz/importer)\
Contains a framework to load, convert and store data into database entities. Depending on the underlying database it
permits to maximize performance by using prepared statements and batch updates.
* [System Protocol Facility](src/main/java/sirius/biz/protocol)\
Provides various facilities which record and store system events like log messages, sent mails, service incidents or 
security related events. 
* [Multi Tenant User Management](src/main/java/sirius/biz/tenants)\
Provides a database independent model to represent multiple tenants and users which have access to the system.
Currently implementations for **JDBC** and for **MongoDB** are available.
* [Sequence Generators](src/main/java/sirius/biz/sequences)\
Provides sequence generators for **JDBC** databases and **MongoDB**.
* [Storage System](src/main/java/sirius/biz/storage)\
Contains a facility which stores files / objects in an object store but keeps all metadata in
either a **JDBC** database or **MongoDB** for efficient access. Also provides a management and maintenance UI.
* [Virtual File System Layer](src/main/java/sirius/biz/vfs)\
Provides an abstraction layer for virtual file systems which can be accessed via remote protocols like FTP(S).
* [ObjectStore access](src/main/java/sirius/biz/storage/s3)\
Contains a thin abstraction layer for AWS compatible S3 object stores.
* [Code lists](src/main/java/sirius/biz/codelists)\
Permits to manage and access code lists which are either stored in **JDBC** databases or **MongoDB**.
