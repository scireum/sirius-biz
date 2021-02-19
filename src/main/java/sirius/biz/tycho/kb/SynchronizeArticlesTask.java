/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.tycho.kb;

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
import sirius.pasta.noodle.compiler.CompileException;
import sirius.pasta.noodle.compiler.SourceCodeInfo;
import sirius.pasta.tagliatelle.Tagliatelle;
import sirius.pasta.tagliatelle.Template;
import sirius.pasta.tagliatelle.compiler.TemplateCompilationContext;
import sirius.pasta.tagliatelle.compiler.TemplateCompiler;
import sirius.pasta.tagliatelle.rendering.GlobalRenderContext;
import sirius.web.resources.Resource;
import sirius.web.resources.Resources;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

@Register(framework = KnowledgeBase.FRAMEWORK_KNOWLEDGE_BASE)
public class SynchronizeArticlesTask implements EndOfDayTask {

    private boolean executed = false;

    @Part
    private KeyGenerator keyGenerator;

    @Part
    private Elastic elastic;

    @Part
    private Mixing mixing;

    @Part
    private Resources resources;

    @Part
    private Tagliatelle tagliatelle;

    @Part
    private KnowledgeBase knowledgeBase;

    @Override
    public String getName() {
        return "synchronize-knowledgebase";
    }

    @Override
    public void execute() throws Exception {
        if (executed) {
            return;
        }

        synchronizeArticles();
        executed = Sirius.isProd();
    }

    private void synchronizeArticles() {
        String syncId = keyGenerator.generateId();

        // Compile all templates and update contents in Elastic...
        findAllArticleTemplates().forEach(matcher -> updateArticle(cleanupTemplatePath(matcher.group(0)), syncId));

        // Force Elastic to digest our updates...
        elastic.getLowLevelClient().refresh(mixing.getDescriptor(KnowledgeBaseEntry.class).getRelationName());

        // Remove outdated data...
        cleanupOldEntries(syncId);
        cleanupEmptyChapters();

        // Check direct references (k:ref)...
        checkTextReferences();

        // Check the consistency...
        checkCrossReferences();
    }

    private Stream<Matcher> findAllArticleTemplates() {
        return Sirius.getClasspath().find(Pattern.compile("(default/|customizations/[^/]+/)?kb/.*\\.pasta"));
    }

    private String cleanupTemplatePath(String templatePath) {
        if (templatePath.startsWith("default/")) {
            return "/" + templatePath.substring(8);
        }
        return "/" + templatePath;
    }

    private void updateArticle(String templatePath, String syncId) {
        try {
            Template template = compileTemplate(templatePath);
            GlobalRenderContext context = tagliatelle.createRenderContext();
            template.render(context);

            String articleId = Strings.split(template.getShortName(), ".").getFirst().toUpperCase();
            String lang = context.getExtraBlock("lang");
            KnowledgeBaseEntry entry = findOrCreateEntry(templatePath, articleId, lang, syncId);

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
        } catch (CompileException e) {
            KnowledgeBase.LOG.WARN("Failed to compile article %s: %s", templatePath, e.getMessage());
        } catch (Exception e) {
            Exceptions.handle()
                      .to(KnowledgeBase.LOG)
                      .error(e)
                      .withSystemErrorMessage("Failed to load article %s: %s (%s)", templatePath)
                      .handle();
        }
    }

    private Template compileTemplate(String templatePath) throws CompileException {
        Resource templateResource = resources.resolve(templatePath)
                                             .orElseThrow(() -> new IllegalArgumentException(
                                                     "Cannot resolve articel template."));
        Template template = new Template(templatePath, templateResource);
        TemplateCompilationContext compilationContext =
                new TemplateCompilationContext(template, SourceCodeInfo.forResource(templateResource), null);
        TemplateCompiler compiler = new TemplateCompiler(compilationContext);
        compiler.compile();
        compiler.getContext().processCollectedErrors();
        return template;
    }

    private KnowledgeBaseEntry findOrCreateEntry(String templatePath, String articleId, String lang, String syncId) {
        KnowledgeBaseEntry entry = elastic.select(KnowledgeBaseEntry.class)
                                          .eq(KnowledgeBaseEntry.ARTICLE_ID, articleId)
                                          .eq(KnowledgeBaseEntry.LANG, lang)
                                          .queryFirst();
        if (entry == null) {
            entry = new KnowledgeBaseEntry();
            entry.setArticleId(articleId);
            entry.setLang(lang);
        } else if (!Strings.areEqual(templatePath, entry.getTemplatePath()) && Strings.areEqual(entry.getSyncId(),
                                                                                                syncId)) {
            throw Exceptions.handle()
                            .withSystemErrorMessage(
                                    "KnowledgeBase detected an article id collision for id %s: %s vs %s (Language: %s)",
                                    articleId,
                                    templatePath,
                                    entry.getTemplatePath(),
                                    lang)
                            .handle();
        }

        entry.setTemplatePath(templatePath);

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

    private void checkTextReferences() {
        elastic.select(KnowledgeBaseEntry.class).iterateAll(this::checkArticle);
    }

    private void checkArticle(KnowledgeBaseEntry article) {
        KnowledgeBaseArticle articleUnderTest = new KnowledgeBaseArticle(article, knowledgeBase);
        knowledgeBase.installCurrentArticle(articleUnderTest);
        try {
            compileTemplate(article.getTemplatePath()).render(tagliatelle.createRenderContext());
            if (!articleUnderTest.getUnresolvedReferences().isEmpty()) {
                KnowledgeBase.LOG.WARN("The article %s has unresolved references: %s",
                                       article.getTemplatePath(),
                                       Strings.join(articleUnderTest.getUnresolvedReferences(), ", "));
            }
        } catch (Exception e) {
            Exceptions.handle()
                      .to(KnowledgeBase.LOG)
                      .error(e)
                      .withSystemErrorMessage("Failed to check article %s: %s (%s)", article.getTemplatePath())
                      .handle();
        } finally {
            knowledgeBase.installCurrentArticle(null);
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
