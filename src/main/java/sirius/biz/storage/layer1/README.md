# Layer 1 - Object Store

This is the lowest layer of the [Storage Framework](../) which provides an API which permits to store objects (chunks of binary data).

Using an API like this enables the framework to store the objects in an **Amazon S3** compatible store. Using
the [FSStorageEngine](FSStorageEngine.java) however, we can also emulate this API and place the objects in
the local file system.

Additionally this layer provides a [replication engine](replication/), which will copy all objects in one space 
(which is a **bucket** in S3 terminology) into another - again both can be either stored locally,
reside in the same cloud or different ones. Via the delivery method provided by [ObjectStorageSpace](ObjectStorageSpace.java)
we have a fault tolerant way of sending the object data out as response to a HTTP request.

Note that the intention is to only store an object once. So once the contents of an object changes,
its key should also change. This is not enforced by the API or the replication system in any way, but
it permits to provide a very efficient caching strategy when serving the contents via HTTP, as the max-age
can be set to a very large number. Using the [layer 2](../layer2/) which separates virtual blob keys
(used to access the metadata) and the **physicalObjectKeys** which resemble the object keys here, this
'write once' policy is supported by default.

This layer is most probably not accessed by external code directly, however, the config section
in [component-biz.conf](../../../../../resources/component-biz.conf) provides a good overview of the
required and recommended settings.
