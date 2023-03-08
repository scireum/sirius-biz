# Tycho Metric Dashboards

This provides sort of a link between the [Metrics](../../analytics/metrics) or the [Events](../../analytics/Events)
framework and the actual Tycho UI. This framework can be used to define locations in the UI to output key metrics
for a target (e.g. an entity). This can be achieved using the tags &lt;t:keyMetrics&gt; or &lt;t:sidebarKeyMetrics&gt;.
Tycho itself defines a standard location on the main dashboard (for important key metrics) using the global target "-".

Subclasses of [KeyMetricProvider](KeyMetricProvider.java) can then be used to make key metrics visible in these 
locations. For the global Tycho ones, [GlobalKeyMetricProvider](GlobalKeyMetricProvider.java) can be used.
