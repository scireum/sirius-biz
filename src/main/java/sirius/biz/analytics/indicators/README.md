# Indicators

Provides a framework which executes [indicators](Indicator.java) for an [entity](IndicatedEntity.java)
and stores their result as [IndicatorData](IndicatorData.java).

Normally, indicators are executed when the entity is saved. However, for complex computations
**batch indicators** can be used. This requires that a [BatchIndicatorCheck](BatchIndicatorCheck.java)
is provided (subclassed) for the entity being checked.

This framework relies on the [Distributed Data Checks](../checks) framework.

Note that indicators are stored as a list of strings which permits to
query them efficiently but also requires a database which capable of storing them.
Therefore this framework can currently only be used with **MongoDB**.
