---
code: SHJEK
lang: en
title: Troubleshooting
description: Provides a list of tips and hits in case of a system malfunction.
parent: DJELK
priority: 10
permissions: flag-system-tenant
chapter: false
---
## Monitoring

Each node provides a distinct endpoint, which can be used by external monitoring tools:
`/system/monitor`. (See: [System Health API](/system/api/health)).
For upstream load-balancers (which want to ensure to only forward request to healthy nodes), either
`/system/ok` or `/system/cluster/ready` can be used.
([Cluster Health API](/system/api/cluster)).
Metric aggregation tools can pull all internally recorded metrics via `/system/metrics`.
This will output a **Prometheus** compatible format. Note that one might want to block outside access to
this URI.

## Measuring System Performance

The system performance can best be viewed using the [System State](/system/state) view.
Additionally, the [Timing](/system/timing) view can be used to enable an in-depth
monitoring for all system tasks. This can be enabled and observed at runtime, with minimal overhead. Finally the
overall system load (especially of the background tasks) can be inspected using the
[System Load](/system/load) view.
