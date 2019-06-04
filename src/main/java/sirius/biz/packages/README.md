# Packages

This package helps handling packages and upgrades.

## What are 'Packages' and 'Updates'?

*Packages* and *Upgrades* are a high level view of actual permissions. Permissions can be bundled under packages and/or
upgrades. If a e.g. Tenant has a certain package and certain upgrades the permissions it has will be calculated on the 
basis of those.

One of the advantages of a package/upgrades approach is, that a customer can have a package and a small set of upgrades
instead of configuring a huge list of permissions for every customer. Also upgrades and packages reflect reality more 
realistically most of the time.

Every e.g. Tenant can only have one package selected at any given time. Packages could be things like "MyApp-Basic",
"MyApp-Pro" and "MyApp-Enterprise".

Upgrades are additional features a e.g. Tenant has. There can be multiple upgrades selected at any given time.

## Usage

The available packages and upgrades of an given scope can be configured via `security.packages.<scope>.packages` and
`security.packages.<scope>.upgrades`. You can access this information with the `Packages#getPackages` and
`Packages#getUpgrades` methods within the [Packages](Packages.java) class.

What permissions are actually granted by a given upgrade/packages is configured via `security.profiles`.

One last thing which can be configured is `security.packages.required-permissions-for-permission`. If a certain 
permission needs another permission to make sense, this can be configured here. This can be useful for example if a 
UserAccount role should be only displayed if the Tenant has a certain other permission. This can be checked via the
`Packages#hasRequiredPermissionForPermission` method.

To use packages and upgrades in entities, use the [PackageData](PackageData.java)-composite.

Example configuration:
```
security {

    packages {
        tenant {
            packages = ["package-basic", "package-pro"]
            upgrades = ["upgrade-a", "upgrade-b"]
        }
        
        required-permissions-for-permission {
            permission-q = "required-permission-xyz"
        }
    }

    profiles {
        package-basic {
            permission-0: true
        }
    
        package-pro {
            package-basic: true
            upgrade-b: true
            permisssion-1: true
        }
        
        upgrade-a {
            permisssion-2: true
        }
        
        upgrade-b {
            permisssion-3: true
            permisssion-4: true
        }
    }
}
```
