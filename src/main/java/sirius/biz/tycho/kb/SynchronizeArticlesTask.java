/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.tycho.kb;

import sirius.biz.locks.Locks;
import sirius.db.KeyGenerator;
import sirius.db.es.Elastic;
import sirius.db.mixing.Mixing;
import sirius.kernel.Sirius;
import sirius.kernel.commons.Strings;
import sirius.kernel.commons.Value;
import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.Priorized;
import sirius.kernel.di.std.Register;
import sirius.kernel.health.Exceptions;
import sirius.kernel.timer.EndOfDayTask;
import sirius.tagliatelle.Tagliatelle;
import sirius.tagliatelle.Template;
import sirius.tagliatelle.rendering.GlobalRenderContext;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.regex.Pattern;

@Register(framework = KnowledgeBase.FRAMEWORK_KNOWLEDGE_BASE)
public class SynchronizeArticlesTask implements EndOfDayTask {

    private boolean executed = false;

    @Part
    private Locks locks;

    @Part
    private KeyGenerator keyGenerator;

    @Part
    private Elastic elastic;

    @Part
    private Mixing mixing;

    @Part
    private Tagliatelle tagliatelle;

    @Override
    public String getName() {
        return "synchronize-knowledgebase";
    }

    @Override
    public void execute() throws Exception {
        if (executed) {
            return;
        }

        //TODO perform this for all EODtasks
        if (!locks.tryLock("sync-knowledgebase", Duration.ofSeconds(5))) {
            return;
        }

        try {
            synchronizeArticles();
            //  executed = true;
        } finally {
            locks.unlock("sync-knowledgebase");
        }
    }

    private void synchronizeArticles() {
        String syncId = keyGenerator.generateId();
        Sirius.getClasspath()
              .find(Pattern.compile("(default/|customizations/[^/]+/)?kb/.*\\.pasta"))
              .forEach(matcher -> updateArticle(cleanupTemplatePath(matcher.group(0)), syncId));

        elastic.getLowLevelClient().refresh(mixing.getDescriptor(KnowledgeBaseEntry.class).getRelationName());
        cleanupOldEntries(syncId);
        cleanupEmptyChapters();
        checkCrossReferences();
    }

    private String cleanupTemplatePath(String templatePath) {
        if (templatePath.startsWith("default/")) {
            return "/" + templatePath.substring(8);
        }
        return "/" + templatePath;
    }

    private void updateArticle(String templatePath, String syncId) {
        try {
            Template template = tagliatelle.resolve(templatePath).orElseThrow(() -> new IllegalArgumentException(""));
            GlobalRenderContext context = tagliatelle.createRenderContext();
            template.render(context);

            String articleId = Strings.split(template.getShortName(), ".").getFirst().toUpperCase();
            String lang = context.getExtraBlock("lang");
            KnowledgeBaseEntry entry = findOrCreateEntry(templatePath, articleId, lang);

            entry.setSyncId(syncId);
            entry.setChapter(Value.of(context.getExtraBlock("chapter")).asBoolean());
            entry.setParentId(Value.of(context.getExtraBlock("parent")).toUpperCase());
            entry.setPriority(Value.of(context.getExtraBlock("priority")).asInt(Priorized.DEFAULT_PRIORITY));
            entry.setTitle(context.getExtraBlock("title"));
            entry.setDescription(context.getExtraBlock("description"));
            entry.setRequiredPermissions(context.getExtraBlock("requiredPermissions"));
            Arrays.stream(context.getExtraBlock("crossReferences").split(","))
                  .map(String::trim)
                  .map(String::toUpperCase)
                  .filter(Strings::isFilled)
                  .forEach(crossReference -> entry.getRelatesTo().modify().add(crossReference));

            elastic.update(entry);
        } catch (Exception e) {
            Exceptions.handle()
                      .to(KnowledgeBase.LOG)
                      .error(e)
                      .withSystemErrorMessage("Failed to load article %s: %s (%s)", templatePath)
                      .handle();
        }
    }

    private KnowledgeBaseEntry findOrCreateEntry(String templatePath, String articleId, String lang) {
        KnowledgeBaseEntry entry = elastic.select(KnowledgeBaseEntry.class)
                                          .eq(KnowledgeBaseEntry.ARTICLE_ID, articleId)
                                          .eq(KnowledgeBaseEntry.LANG, lang)
                                          .queryFirst();
        if (entry == null) {
            entry = new KnowledgeBaseEntry();
            entry.setArticleId(articleId);
            entry.setLang(lang);
            entry.setTemplatePath(templatePath);
        } else if (!Strings.areEqual(templatePath, entry.getTemplatePath())) {
            throw Exceptions.handle()
                            .withSystemErrorMessage(
                                    "KnowledgeBase detected an article id collision for id %s: %s vs %s (Language: %s)",
                                    articleId,
                                    templatePath,
                                    entry.getTemplatePath(),
                                    lang)
                            .handle();
        }
        return entry;
    }

    private void blockwiseIterate(Consumer<KnowledgeBaseEntry> entryProcessor) {
        String lastArticleId = KnowledgeBase.ROOT_CHAPTER_ID;
        while (true) {
            List<KnowledgeBaseEntry> entries = elastic.select(KnowledgeBaseEntry.class)
                                                      .where(Elastic.FILTERS.gt(KnowledgeBaseEntry.ARTICLE_ID,
                                                                                lastArticleId))
                                                      .orderAsc(KnowledgeBaseEntry.ARTICLE_ID)
                                                      .limit(100)
                                                      .queryList();
            if (entries.isEmpty()) {
                return;
            }
            entries.forEach(entryProcessor);
            lastArticleId = entries.get(entries.size() - 1).getArticleId();
        }
    }

    private void cleanupOldEntries(String syncId) {
        blockwiseIterate(entry -> {
            if (!Strings.areEqual(syncId, entry.getSyncId())) {
                KnowledgeBase.LOG.INFO("Deleting outdated entry %s (%s) in language (%s)",
                                       entry.getArticleId(),
                                       entry.getTitle(),
                                       entry.getLang());

                elastic.delete(entry);
            }
        });
    }

    private void cleanupEmptyChapters() {
        AtomicBoolean performCheck = new AtomicBoolean(true);
        while (performCheck.get()) {
            performCheck.set(false);
            blockwiseIterate(entry -> {
                if (entry.isChapter()) {
                    boolean hasChildren = elastic.select(KnowledgeBaseEntry.class)
                                                 .eq(KnowledgeBaseEntry.PARENT_ID, entry.getArticleId())
                                                 .eq(KnowledgeBaseEntry.LANG, entry.getLang())
                                                 .exists();
                    if (!hasChildren) {
                        KnowledgeBase.LOG.INFO("Deleting empty chapter %s (%s) in language %s",
                                               entry.getArticleId(),
                                               entry.getTitle(),
                                               entry.getLang());
                        elastic.delete(entry);
                        performCheck.set(true);
                    }
                }
            });

            if (performCheck.get()) {
                elastic.getLowLevelClient().refresh(mixing.getDescriptor(KnowledgeBaseEntry.class).getRelationName());
            }
        }
    }

    private void checkCrossReferences() {
        blockwiseIterate(entry -> {
            boolean parentExists = elastic.select(KnowledgeBaseEntry.class)
                                          .eq(KnowledgeBaseEntry.ARTICLE_ID, entry.getParentId())
                                          .exists();
            if (!parentExists) {
                KnowledgeBase.LOG.WARN("The article %s (%s) references a non-existent parent: %s",
                                       entry.getArticleId(),
                                       entry.getTitle(),
                                       entry.getParentId());
            }

            entry.getRelatesTo().forEach(crossReference -> {
                boolean rerefenceExists = elastic.select(KnowledgeBaseEntry.class)
                                                 .eq(KnowledgeBaseEntry.ARTICLE_ID, crossReference)
                                                 .exists();
                if (!rerefenceExists) {
                    KnowledgeBase.LOG.WARN("The article %s (%s) contains a non-existent cross reference to: %s",
                                           entry.getArticleId(),
                                           entry.getTitle(),
                                           crossReference);
                }
            });
        });
    }
}
