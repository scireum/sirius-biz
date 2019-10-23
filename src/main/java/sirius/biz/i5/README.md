# IBM i5 Connectivity

This set of helper classes let's you talk to the last exisiting dinosaurs :).
It uses the **jt400** driver provided by IBM. Using the [I5Connector](I5Connector.java)
a pooled [I5Connection](I5Connection.java) can be obtained. This can be used to
read message queues or call programs on the big iron.

Using the [Transformer](Transformer.java) a plain Java object which fields are
annotated with [Transform](Transform.java) annotations can be serialized and
deserialized to and from byte arrays understood by the i5.

Using the [i5](I5Command.java) in the [System Console](http://localhost:9000/system/console)
provides a list of open connections along with the job number provided by the i5.

Also the number of calls and the utilization are reported to http://localhost:9000/system/state
and the metrics collector via the [I5MetricProvider](I5MetricProvider.java).
