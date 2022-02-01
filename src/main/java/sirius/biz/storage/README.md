# Storage Facility

This framework is responsible for storing, managing and retrieving binary data. As it covers quite
a range of functionality it is split into three layers which build on top of each other.

## Layer 1 - Object Store

The lowest layer provides an API to store objects (chunks of binary data).
The main entry point is [ObjectStorage](layer1/ObjectStorage.java) which provides access to the configured
storage spaces. A space can be compared to **buckets** in the Amazon S3 terminology.

More details can be found in the [Layer 1 documentation](layer1/).

## Layer 2 - Blob Storage

This layer uses the **layer 1** to actually store the binary objects but also provides a way to store metadata
in either a **JDBC datasource** or **MongoDB**.

The main entry point is [BlobStorage](layer2/BlobStorage.java) which provides access to the individual
[BlobStorageSpace](layer2/BlobStorageSpace.java).

This layer is the central part of the storage framework as it provides utilities to reference blobs in entities,
an HTTP dispatcher for efficient access and delivery as well as a conversion engine which provides variants
of the stored blobs (e.g. a properly resized JPG image of a given EPS file).

More details can be found in the [Layer 2 documentation](layer2/).

## Layer 3 - Virtual File System

The virtual file system (VFS) provides a unified interface for all parts of the system which either provide or
consume files or file systems. Having a built-in **FTP** and **SSH** server, it supports external applications
access via familiar protocols such as **FTP, FTPS, SFTP, SCP**. Besides, it can mount external file systems via
**FTP, FTPS, SFTP, SCP** and **CIFS** to make external files available to internal sub systems.

Being a virtual file system, neither folders nor files have to exist anywhere physically. A [VFSRoot](layer3/VFSRoot.java)
can provide artificial folders and files and directly process the provided data.

A central root which is automatically provided is the [L3Uplink](layer2/L3Uplink.java) of layer 2 which makes
**browsable** spaces visible as folders in the VFS.

Note that the VFS also provides a management UI via http://localhost:9000/fs.

More details can be found in the [Layer 3 documentation](layer3/).

## Utilities

The [util](util/) package provides some helper classes which are mainly used by the framework itself and
in most cases shouldn't be accessed externally.

## S3 API

The [s3](s3/) package provides a facility to manage and access **Amazon S3** compatible object stores. In
most cases it is advisable to either use the **layer 1** or **layer 2** as these provide additional functionalities
such as replication, API emulation in the local file system or an efficient way of storing metadata.
