# Layer 2 - Blob Storage

This is the **layer 2** of the [Storage Framework](../). It permits to store [blobs](Blob.java) and
even [directories](Directory.java). The metadata is either stored in a **JDBC datasource** or **MongoDB**.
The actual binary data is stored by the [layer 1](../layer1/).

To use a  **JDBC datasource** as storage engine the framework **biz.storage-blob-jdbc** must be enabled.
When using **MongoDB** the name of the framework to enable is **biz.storage-blob-mongo**.

The main entry point is [BlobStorage](layer2/BlobStorage.java) which provides access to the individual
[BlobStorageSpace](layer2/BlobStorageSpace.java).

However, most probably this framework or layer will be accessed via three other central parts.
The first are direct references from entities to blobs. This is supported via either a [BlobHardRef](BlobHardRef.java)
or a [BlobSoftRef](BlobSoftRef.java). Where the former lets a user pick an existing objects and associate
it with an entity and the latter is used to directly upload a file to be associated. This file will not be
visible outside of this reference and also deleted along with the entity.

If an entity references many files a [BlobContainer](BlobContainer.java) can be used to associate any number
of blobs to an entity. As when using a **BlobHardRef** these blobs aren't visible anywhere else and will also
auto-deleted once the entity is deleted.

The third way to access the blobs and directories is by setting the **browsable** flag of a storage space
(in the system configuration). This will be picked up by the [L3Uplink](L3Uplink.java) and thus make the
blobs and directories visible as a virtual file system.

A notable example of this pattern is the **work** space which is enabled by default and used by jobs to
import data from and export data to.

Blobs can also be service via HTTP bei using the [URLBuilder](URLBuilder.java) which most probably
generates an URL served by the [BlobDispatcher](BlobDispatcher.java). Next to serving the raw data,
the framework permits to generate on demand [variants](variants/BlobVariant.java) via one or more
[converters](variants/Converter.java). These converters and configured via the system configuration
and managed by the [ConversionEngine](variants/ConversionEngine.java).
