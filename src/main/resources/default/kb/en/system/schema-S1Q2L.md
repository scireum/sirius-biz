---
code: S1Q2L
lang: en
title: Database Schema
description: Describes the functionality of the database schema.
parent: DJELK
priority: 100
permissions: flag-system-tenant
chapter: false
---
The [Database Schema](/system/schema) can be used to view all schema changes
of the databases used in the system and execute them via the system.

The listed changes to the database schema are recommended so that the application can be executed.

Please note that manually created tables may also be proposed for deletion.
Here you should check whether these tables should really be deleted.

In any case, it is recommended to perform a **database backup** before making changes.

Some changes, such as deleting a column, may only be executed after dependent changes
(e.g. deleting **FOREIGN KEYs**) have been made.
