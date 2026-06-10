---
code: T2IQI
lang: en
title: Tagliatelle Template Engine
description: Defines the syntax of the Tagliatelle Template Engine.
parent: AHJEC
priority: 100
permissions: ""
chapter: false
crossReferences:
  - HP9RC
---
## Common

**Tagliatelle** is a template engine which renders statically typed and compiled templates. This is
mainly used to render the HTML output of the web-server but can be used to generate any form of text output.

Tagliatelle uses Noodle (<kba:HP9RC>) as expression language. An expression can be placed anywhere
in the template by prefixing it with an **@**, i.e. `@myVariable`. If the
boundaries of the expression are unclear, it can be put into parentheses like
`@(3+4)`. If a tag expects an expression anyway, the "@" can be omitted. (Note
that the "@" can be escaped by putting two in a row: `@@`.

The main part of the templates are made up of special **tags** (hence the name Tagliatelle). These tags
can either be built-in (e.g. `<i:if test="1 < 2">...</i:if>`) or
they can be defined by templates which are placed in the directory `tags/PREFIX/tagName.html.pasta`.

## Anatomy of a tag

Tags are organized in **tag libraries**. Each library has a prefix assigned, e.g. **k**. All templates
for this tag library are then placed in `tags/k/tagName.html.pasta` and can be
referenced using `<k:tagName>`.

If a tag supports custom attributes, these are defined in the template via
`<i:arg type="Class" name="Name" />`. The body of a tag can be rendered
by the tag itself by calling `<i:render name="body" />`. Note that the
caller of a tag can supply additional blocks using `<i:block name="myBlock">...</i:block>`.
This can then be used by the tag via `<i:render name="myBlock" />`.

## Available tags and macros

A list of all available tags and macros can be found here:
[Tagliatelle Tag Overview](/system/tags)
