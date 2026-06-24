---
code: HP9RC
lang: en
title: Noodle Scripting
description: Defines the syntax of the Noodle Scripting Language.
parent: AHJEC
priority: 100
permissions: ""
chapter: false
---
## Common

**Noodle** is a statically typed and compiled language which tries to resemble **Java** as much
as possible. Note however, that some simplifications are present.

Noodle supports the following expressions:

- Atoms like numbers, Strings or boolean values: `42, "Hello", true`
- Class literals to reference a Java-Class: `Strings.class`
- Lambda expressions are supported, but use a simpler syntax than Java: `| arg1, arg2 | { arg1 + arg2 }`
- Common boolean and numeric operations are supported: `3 + 4` or `a && b`
- Method calls: `"test".toUpperCase()` or `Strings.isEmpty("test")`
- Macro calls: `isEmpty("test")`. All available macros are listed here:
[Tagliatelle Tag Overview](/system/tags)
- If statements: `if (condition) { statements; }`
- For statements: `for (Type variable : iterable) { loop-statements; }`
- Return statements: `return expression`

Noodle supports line comments, starting with `//` and block comments
wrapped in `/*` and `*/`.

## Special Syntax and Semantics

In contrast to Java `==` will invoke `equals` instead
of checking for object identity. Therefore, this can be used for Strings directly.

Noodle will deduce the types as much as possible. If however a type check or a cast becomes necessary, the
functions `x.is(Some.class)` and `x.as(Some.class)`
can be used to check for or cast to the given type.
