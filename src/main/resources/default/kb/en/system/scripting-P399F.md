---
code: P399F
lang: en
title: Scripting
description: Describes the scripting functionalities provided by SIRIUS.
parent: DJELK
priority: 100
permissions: flag-system-tenant
chapter: false
---
The [Scripting IDE](/system/scripting) can be used to run administrative scripts
on any selected node of the system. Scripts are authored using the <kba:HP9RC> language.

Once a script is executed, it is assigned with a unique identifier, which used when logging into the
transcript. Note that all log messages are visible on each node, independent of the actual node which
executes the script.

If a script mis-behaves, it can be killed via another script using the thread ID which is logged when
the execution starts. Note however, that this script has to run on the same node as the offending one.

```
sirius.kernel.async.CallContext.getContext(THREAD_ID).get().get(sirius.kernel.async.TaskContext.class).cancel()
```

Note, if the **tenants** and the **processes** framework are both enabled, the script can be run
as process, which permits communicating with the outside world via **/ps**:

```
part(Tenants.class).runAsAdminProcess('My Process', | ps | {
    log('Hello From the Other Side');
});
```

Also note, that these scripts can be stopped via the processes UI (just like any other process).
