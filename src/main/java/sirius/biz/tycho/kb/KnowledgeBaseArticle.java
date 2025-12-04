/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.tycho.kb;

import sirius.biz.web.BizController;
import sirius.kernel.commons.Explain;
import sirius.kernel.commons.Files;
import sirius.kernel.commons.Strings;
import sirius.kernel.di.std.Part;
import sirius.pasta.noodle.compiler.CompileException;
import sirius.pasta.tagliatelle.Tagliatelle;
import sirius.pasta.tagliatelle.Template;
import sirius.web.resources.Resource;

import java.net.URL;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Represents an article or chapter in the {@link KnowledgeBase}.
 * <p>
 * This is mainly a wrapper around {@link KnowledgeBaseEntry} which also drags around the effective language
 * which has been used during the lookup.
 * <p>
 * It provides a bunch of convenience methods which are used within the templates.
 */
public class KnowledgeBaseArticle {

    private final String language;
    private final KnowledgeBaseEntry entry;
    private final KnowledgeBase knowledgeBase;

    /**
     * Pattern which is used to extract project and path from a jar URL, as typically seen for packaged dependencies or
     * on a server.
     * <p>
     * Example: {@code jar:file:/Users/jakob/.m2/repository/com/scireum/sirius-biz/DEVELOPMENT-SNAPSHOT/sirius-biz-DEVELOPMENT-SNAPSHOT.jar!/default/kb/en/system/scripting-P399F.html.pasta}
     */
    @SuppressWarnings("java:S5852")
    @Explain("We only match the names of internal resources, not user input. "
             + "A denial-of-service attack via backtracking is not possible.")
    private static final Pattern JAR_URL_PATTERN = Pattern.compile("file:(?<jar>.+)!/(?<path>.+)");

    /**
     * Pattern which is used to extract project and path from a file URL, as typically seen during development.
     * <p>
     * Example: {@code file:///Users/jakob/Development/memoio/target/classes/kb/de/integration-manual/deep-integration/iam-integration-JURIH.html.pasta}
     */
    @SuppressWarnings("java:S5852")
    @Explain("We only match the names of internal resources, not user input. "
             + "A denial-of-service attack via backtracking is not possible.")
    private static final Pattern FILE_URL_PATTERN = Pattern.compile("(?<directory>.+)/target/classes/(?<path>.+)");

    /**
     * Suffix which is used to identify development snapshot versions.
     */
    private static final String DEVELOPMENT_SNAPSHOT_SUFFIX = "-DEVELOPMENT-SNAPSHOT".toLowerCase();

    /**
     * Pattern which is used to strip version suffixes from jar file names.
     * <p>
     * Example: {@code sirius-biz-1.2.3a.jar} or {@code sirius-biz-dev-1.2.3.jar}
     */
    @SuppressWarnings("java:S5852")
    @Explain("We only match the names of internal resources, not user input. "
             + "A denial-of-service attack via backtracking is not possible.")
    private static final Pattern VERSION_SUFFIX_PATTERN = Pattern.compile("(?<project>.+)(?:-dev)?(?:-ga)?-\\d+.*$");

    @Part
    private static Tagliatelle tagliatelle;

    /**
     * Creates a new article.
     *
     * @param entry         the entry to wrap
     * @param language      the language code which has been used to lookup the article
     * @param knowledgeBase the instance of the knowledge base used to perform further lookups
     */
    public KnowledgeBaseArticle(KnowledgeBaseEntry entry, String language, KnowledgeBase knowledgeBase) {
        this.entry = entry;
        this.language = language;
        this.knowledgeBase = knowledgeBase;
    }

    /**
     * Returns an absolute URL which can be used to view this article without logging in.
     *
     * @return the pre-signed absolute URL to this article
     */
    public String getPresignedUrl() {
        return BizController.getBaseUrl()
               + "/kba/"
               + language
               + "/"
               + getArticleId()
               + "/"
               + computeAuthenticationSignature(true);
    }

    /**
     * Generates an edit URL for this article, currently a deep link into GitHub.
     *
     * @return the edit URL for this article or <tt>null</tt> if no such URL can be generated
     */
    public String generateEditUrl() {
        try {
            return tagliatelle.resolve(entry.getTemplatePath())
                              .map(Template::getResource)
                              .map(Resource::getUrl)
                              .map(KnowledgeBaseArticle::extractProjectAndPathFromResourceUrl)
                              .map(ProjectAndPath::toGithubUrl)
                              .orElse(null);
        } catch (CompileException _) {
            return null;
        }
    }

    /**
     * Computes the authentication signature.
     *
     * @param thisMonth determines if the signature is computed for the current month (<tt>true</tt>) or the last month
     *                  (<tt>false</tt>)
     * @return the authentication signature for this article
     */
    public String computeAuthenticationSignature(boolean thisMonth) {
        LocalDate date = LocalDate.now();
        if (!thisMonth) {
            date = date.minusMonths(1);
        }

        return Strings.limit(BizController.computeConstantSignature(getArticleId()
                                                                    + date.getYear()
                                                                    + date.getMonthValue()), 5);
    }

    /**
     * Returns all child chapters for this article.
     *
     * @return a list of all child chapters for this article
     */
    public List<KnowledgeBaseArticle> queryChildChapters() {
        return knowledgeBase.queryChildChapters(this);
    }

    /**
     * Returns all child articles for this article.
     *
     * @return a list of all child articles for this article
     */
    public List<KnowledgeBaseArticle> queryChildren() {
        return knowledgeBase.queryChildArticles(this);
    }

    /**
     * Returns a list of all cross references.
     *
     * @return a list of all articles which are either referenced by this article or which reference this article
     * by themselves.
     */
    public List<KnowledgeBaseArticle> queryCrossReferences() {
        return knowledgeBase.queryCrossReferences(this);
    }

    /**
     * Tries to resolve the parent article.
     *
     * @return the parent of this article, or an empty optional if the current article is already the root chapter or
     * not placed in any chapter at all.
     */
    public Optional<KnowledgeBaseArticle> queryParent() {
        return knowledgeBase.resolve(language, entry.getParentId(), false);
    }

    /**
     * Tries to resolve the parent structure of this article.
     *
     * @return a list of the roots of this article (ordered from near to far), or an empty list if the current article
     * is already the root chapter or not placed in any chapter at all.
     */
    public List<KnowledgeBaseArticle> queryParents() {
        List<KnowledgeBaseArticle> parents = new ArrayList<>();

        KnowledgeBaseArticle parent = queryParent().orElse(null);
        while (parent != null) {
            parents.addFirst(parent);
            parent = parent.queryParent().orElse(null);
        }

        return parents;
    }

    public String getArticleId() {
        return entry.getArticleId();
    }

    public String getTitle() {
        return entry.getTitle();
    }

    public String getDescription() {
        return entry.getDescription();
    }

    public String getTemplatePath() {
        return entry.getTemplatePath();
    }

    public String getLanguage() {
        return language;
    }

    protected KnowledgeBaseEntry getEntry() {
        return entry;
    }

    public boolean isChapter() {
        return entry.isChapter();
    }

    public int getPriority() {
        return entry.getPriority();
    }

    /**
     * Extracts the project name and the path within the project from the given resource URL.
     *
     * @param url the resource URL to extract the project and path from
     * @return the extracted project and path or <tt>null</tt> if the URL is invalid or the project/path
     * could not be determined
     */
    private static ProjectAndPath extractProjectAndPathFromResourceUrl(URL url) {
        if (url == null) {
            return null;
        }

        return switch (url.getProtocol()) {
            case "file" -> {
                Matcher matcher = FILE_URL_PATTERN.matcher(url.getPath());
                yield matcher.matches() ?
                      new ProjectAndPath(extractProjectFromFile(matcher.group("directory")), matcher.group("path")) :
                      null;
            }
            case "jar" -> {
                Matcher matcher = JAR_URL_PATTERN.matcher(url.getPath());
                yield matcher.matches() ?
                      new ProjectAndPath(extractProjectFromJar(matcher.group("jar")), matcher.group("path")) :
                      null;
            }
            default -> null;
        };
    }

    /**
     * Attempts to extract the project name from a file path, assuming that the local copy uses the name of the project
     * for the root folder.
     *
     * @param path the file path to extract the project name from
     * @return the extracted project name
     */
    private static String extractProjectFromFile(String path) {
        return Files.getFilenameAndExtension(path);
    }

    /**
     * Attempts to extract the project name from a jar file path, assuming that the jar file uses the name of the
     * project as the base name of the jar, extended by a version string.
     *
     * @param path the jar file path to extract the project name from
     * @return the extracted project name
     */
    private static String extractProjectFromJar(String path) {
        if (!Strings.equalIgnoreCase(Files.getFileExtension(path), "jar")) {
            return null;
        }

        String rawName = Files.getFilenameWithoutExtension(path);
        if (Strings.isEmpty(rawName)) {
            return null;
        }

        if (rawName.toLowerCase().endsWith(DEVELOPMENT_SNAPSHOT_SUFFIX)) {
            return rawName.substring(0, rawName.length() - DEVELOPMENT_SNAPSHOT_SUFFIX.length());
        }

        Matcher matcher = VERSION_SUFFIX_PATTERN.matcher(rawName);
        return matcher.matches() ? matcher.group("project") : null;
    }

    /**
     * Holds the project name and the path within the project.
     *
     * @param project the project name
     * @param path    the path within the project
     */
    private record ProjectAndPath(String project, String path) {
        private String toGithubUrl() {
            if (Strings.isEmpty(project) || Strings.isEmpty(path)) {
                return null;
            }
            return "https://github.com/scireum/" + project + "/edit/develop/src/main/resources/" + path;
        }
    }
}
