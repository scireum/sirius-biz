---
code: 5ZM4U
lang: en
title: System Health Monitoring
description: Describes views and APIs provided to monitor the system health.
parent: DJELK
priority: 100
permissions: flag-system-tenant
chapter: false
crossReferences:
  - SHJEK
---
## System State

The [System State View](/system/state) can be used to show the state of each node
in the cluster along with its most relevant metrics.

## Health API

The [System Health APIs](/system/api/health) can be used to basic monitoring and
also to obtain all recorded metrics in a **Prometheus** compatible format. Note that next to the monitoring
API provided here (`/system/monitor`), the Cluster API <kba:V7FGJ> also
provides a monitoring URL (`/system/cluster/ready`), which takes the node-bleeding into
account. Therefore, the former should be used by monitoring tools, to get notified if a node is down and the latter
should be used by an upstream load-balancer to check the node health/readiness.
