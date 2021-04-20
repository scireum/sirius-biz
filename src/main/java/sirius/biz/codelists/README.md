# Code Lists and Lookup Tables

Provides a framework to retrieve and manage simple masterdata. The more general part of this framework
is [LookupTables](LookupTables.java) and [LookupTable](LookupTable.java)
which provide access to more or less static data like "list of all known countries" etc.

Such data can either be provided via [Jupiter](https://github.com/scireum/jupiter) or by an
internal [CodeList](CodeLists.java).

Note that [LookupValue](LookupValue.java) or [LookupValues](LookupValues.java) can be used to easily reference such
lookup tables from a string column in database entities.

The more specific framework are these [CodeLists](CodeLists.java) which mainly map codes to values. These can either be
global or managed per tenant. Note that code lists are either backed by JDBC/SQL or by MongoDB and either of those
frameworks (*biz.code-lists-jdbc* or *biz.code-lists-mongo*) has to be enabled in order to use code lists.

Note that lookup tables (and also code lists) can be configured using the system config. An example for each section can
be found in [component-biz.conf](../../../../resources/component-biz.conf).
