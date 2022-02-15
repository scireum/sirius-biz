# Tycho Metric Dashboards

This provides sort of a link between the [Metrics](../../analytics/metrics) or the [Events](../../analytics/Events)
framework and the actual Tycho UI. This framework can be used to define locations in the UI to output a metrics
dashboard for a target (e.g. an entity). This can be achieved using the tags &lt;t:keyMetrics&gt; or 
&lt;t:metricsDashboard&gt;. Tycho itself defines a standard location on the main dashboard (for important key metrics)
and the main statistics page for all key metrics and dashboards.

Subclasses of [ChartProvider](ChartProvider.java) and [KeyMetricProvider](KeyMetricProvider.java) can then be used
to make key metrics and charts visible in these locations. For the global Tycho ones, 
[GlobalChartProvider](GlobalChartProvider.java) and [GlobalKeyMetricProvider](GlobalKeyMetricProvider.java) can be used.

