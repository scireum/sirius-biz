# Customization Flags

This package contains the flags which can be used to customize the behavior of the system. Mainly this is used
to enable or disable certain features for specific user groups of the system.

Mainly this is used for migration projects, where some users should be able to already access a new functionality
or also, where some users still may use an old implementation where all others receive the new one.

To achieve this, these flags can be toggled in many locations (the first being present, will be used):
* By either having the string **flag-FLAGNAME-disabled** or **flag-FLAGNAME** in the user agent
* By setting it in the custom config in the current user or its tenant (in the block **flags**)
* By setting it in the scope config
* By setting it in the tenant config which owns the current scope
* By setting it in the config of the system tenant
