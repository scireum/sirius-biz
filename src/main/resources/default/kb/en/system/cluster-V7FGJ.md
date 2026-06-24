---
code: V7FGJ
lang: en
title: Cluster Overview
description: Describes the functionality which comes with the cluster view.
parent: DJELK
priority: 100
permissions: flag-system-tenant
chapter: false
crossReferences:
  - SHJEK
---
## Common

The [Cluster View](/system/cluster) can be used to view all nodes which are visible
to the cluster. It also shows all background tasks and their state/configuration on an individual node. Finally
a list of cluster-wide locks is shown.

## Nodes

The node list shows the software version and uptime of each node. This also permits to remove a stale node from
the cluster (Note: If the node is still physically present, it will re-appear quickly). It also permits to
start "bleeding" of a node. **Bleeding** means, that the node is still continuing to operate but it will
signal to a loadbalancer that it will shutdown soon and won't take any new requests. The same goes for
background tasks - running tasks will be finished, but the node will not pickup any new work.

Bleeding as well as the node state can also be started and observed via the
[Cluster Health API](/system/api/cluster).

## Background Jobs

Lists all jobs which can run in the background. These are:
- **Background Loops**, which more or less run constantly in back background.
- **Queues / Distributed Tasks**, which are job queues that are executed on specific nodes.
- **Daily Tasks**, which run once a day.

Each Job can be globally (cluster-wide) enabled and disabled. Additionally, it can be disabled on a
"per-node" level. Also, the synchronization pattern in general can be chosen. **CLUSTER** means,
that the job ins only executed on one node at the same time. **LOCAL** indicates, that the job
can run on multiple nodes in parallel. **DISABLED** selects, that the job is disabled, either on
this node, or in general.

While the first two settings enabling/disabling a job globally or locally
are intended for system maintenance and ad-hoc management tasks, the synchronization pattern should
be setup permanently. Either via a global config or by using a local **instance.conf** per node.
This can be used to setup the cluster topology so that specific jobs only run on chosen nodes.

## Locks

Show all system-wide obtained locks. Note that the [console](/system/console) command
`locks` shows an equivalent output and also permits to kill a lock forcefully.
