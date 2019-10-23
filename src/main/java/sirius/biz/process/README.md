# Processes Framework

This framework provides a set of helpers to cover the execution of certain tasks. Albeit the name, a process
doesn't actually run or perform any work, it rather is used to report and record whats happening.

The main entry point to this API is [Processes](Processes.java). The framework distinguishes two kinds of
processes. "Normal" ones which are started while executing a background task like a 
[batch job](../jobs/batch/BatchProcessJobFactory.java). These are started, one or more tasks are executed on
one or more nodes which use the [ProcessContext](ProcessContext.java) to report and record data. Then finally
the process is completed and done. 

The second kind are **standby processes**. Whenever a background activity is executed which might need to 
report some findings to the user, it can obtain such a process. This will either be re-activated or created 
(one per tenant and type) and then be used to provide a **process context** to report data and messages. 
An example would be a web service which is invoked every once in a while and which needs to notify an 
administrator if an error happens or which needs to log some data.

Note that once a **process context** is setup, it is also installed as **TaskContext**. Therefore other
frameworks (down to classes in sirius kernnel) can interact with the process (e.g. stop processing once
the process is aborted).

The framework uses **Elasticsearch** as underlying database as it highly depends on its flexible storage
and effeicient search capabilities.

To use the **Processes facility** the framework **biz.processes** has to be enabled. 
