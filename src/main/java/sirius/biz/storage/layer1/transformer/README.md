# Layer 1 - Byte Block Transformers

Provides a set of helpers which permit on the fly compression or encryption of data when storing
objects in a Layer 1 space. This comes in handy when either storing verbose data (like CSV or XML)
or when storing data/backups in a public cloud (which should be encrypted then).

This can be used and applied for primary storage spaces or for backup/replication spaces. Each
space can be compressed, encrypted or both at once.

This is done by specifying the name of the [CipherFactory](CipherFactory.java) as "cipher" which 
creates the appropriate [CipherProvider](CipherProvider.java) used to perform encryption.

Additionally the "compression" setting can be used to select the [CompressionLevel](CompressionLevel.java)
to apply.
