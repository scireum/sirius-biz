# Layer 3 - Virtual File System

This is the **layer 3** of the [Storage Framework](../). It provides a unified interface for all parts of the system
which either provide or consume files or file systems. Having a built-in **FTP** and **SSH** server it supports external
applications access via familiar protocols such as **FTP, FTPS, SFTP, SCP**. Besides it can mount external file systems via
**FTP, FTPS, SFTP, SCP** and **CIFS** to make external files available to internal sub systems.

Being a virtual file system, neither folders nor files have to exists anywhere physically. A [VFSRoot](layer3/VFSRoot.java)
can provide artificial folders and files and directly process the provided data.

The main entrance point to this API is [VirtualFileSystem](VirtualFileSystem.java) which can be used
to browse or resolve files. To provide own directories and files, a [VFSRoot](VFSRoot.java) has to be implemented.

Note that the individual servers (FTP, SSH) provide quite a range of config values. The defaults along
with the documentation for them can be found in [component-biz.conf](../../../../../resources/component-biz.conf).

A management UI is provided via http://localhost:9000/fs.
