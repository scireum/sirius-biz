# Event Recorder

This framework represents the lowest level of the analytics subsystem
as its only purpose is to record and stored recorded events into the
timeseries database [Clickhouse](https://clickhouse.yandex).

The recorded events are available by accessing the **analytics** database
using the [Databases](https://github.com/scireum/sirius-db/blob/main/src/main/java/sirius/db/jdbc/) helper.

Most probably these recorded events will be used by two other frameworks.
These are either [Metrics](../metrics/) or [Jobs](../../jobs/).

To create a new type of events to be recorded, a subclass of [Event](Event.java)
has to be created. Used composites like [WebData](WebData.java) or [UserData](UserData.java)
or create your own to store commonly used data (which will most probably be auto-filled by the save handlers).

Note that the buffer utilization, which is named *events_buffer_usage* can be monitored via
the system metrics reporter (http://localhost:9000/system/state or http://localhost:9000/system/metrics).
