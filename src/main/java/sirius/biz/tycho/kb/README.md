# Tycho Knowledge Base

The knowledge base is an integrated and extensible help center, available to all users of the system.

If enabled (using the framework **tycho.knowledge-base**), all articles placed in the resources folder
**kb** (or any sub-folder within) will be picked up and made available. The knowledge base is reachable via the URI **
kb** or **/kba/LANG/CODE**. Note that the code is a random five-letter code which is assigned to each article. This
permits to rename and re-arrange the physical location of articles without breaking cross-reference consistency. The
language code is a two-letter ISO code to determine which language version to pick. If the article isn't available in
the requested language, the default language (specified in the system config as **knowledgebase.fallbackLanguages**) will be
used.

Note that the articles can be placed in chapters to provide a hierarchical navigation. **Sirius**
itself provides a set of default base chapters where core help topics are placed. These chapters can be extended by
applications.

To create an article, use the **k:article** tag, for chapters use **k:chapter**.

Note that articles are not imediatelly available after a system restart as the end of day task
**synchronize-knowledgebase** has to run. This can be forced by running `eod synchronize-knowledgebase`
in the console.
