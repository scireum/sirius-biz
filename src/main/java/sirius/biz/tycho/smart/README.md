# Smart Values

This tiny framework permits showing string values in the UI by using the `<t:smartValue>` tag. 
These values (e.g. a username) are rendered as link and once the user clicks them, an AJAX call 
is performed using the additionally provided payload data.

This data is resolved into a proper Java object using a [SmartValueResolver](SmartValueResolver.java)
(if necessary). The framework then invokes all [SmartValueProviders](SmartValueProvider.java) to obtain
smart values to show. These are then shown in a tooltip window. A smart value can be an action (like
a "mailto" link or a "tel" link) along with an optional "copy" value to be placed into the clipboard.

This can be used to provide contact data or additional actions in place without consuming large parts
of the screen real estate.
