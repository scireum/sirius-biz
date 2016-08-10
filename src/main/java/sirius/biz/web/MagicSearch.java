/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.web;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONException;
import com.alibaba.fastjson.JSONObject;
import com.google.common.collect.Lists;
import sirius.db.mixing.Column;
import sirius.db.mixing.Entity;
import sirius.db.mixing.SmartQuery;
import sirius.db.mixing.constraints.Like;
import sirius.kernel.health.Exceptions;
import sirius.kernel.xml.StructuredOutput;
import sirius.web.http.WebContext;

import java.util.List;
import java.util.function.Consumer;

/**
 * Helps to parse and process search queries created by the <tt>magicsearch.html</tt> component.
 */
public class MagicSearch {

    public static final String TYPE_QUERY = "query";
    private List<Suggestion> suggestions;

    private MagicSearch() {
    }

    /**
     * Is created to represent a suggestion for a given user input.
     */
    public static class Suggestion {
        private String name;
        private String value;
        private String type;
        private String css;

        /**
         * Creates a new suggestion with the given visible name
         *
         * @param name the name to show to the user.
         */
        public Suggestion(String name) {
            this.name = name;
            this.value = name;
        }

        /**
         * Sets the value to actually send back to the server
         *
         * @param value the value which is acutally suggested
         * @return the instance itself for fluent method calls
         */
        public Suggestion withValue(String value) {
            this.value = value;
            return this;
        }

        /**
         * Sets the type of the suggestion.
         *
         * @param type the type of the suggestion. This can be used to provide several types of suggestions at once and
         *             have several consumers on the server side, which know how to process each.
         * @return the instance itself for fluent method calls
         */
        public Suggestion withType(String type) {
            this.type = type;
            return this;
        }

        /**
         * Sets the css class to apply to the suggestion.
         *
         * @param css the css class used for styling
         * @return the instance itself for fluent method calls
         */
        public Suggestion withCSS(String css) {
            this.css = css;
            return this;
        }

        /**
         * Returns the name of the suggestion.
         *
         * @return the name of the suggestion
         */
        public String getName() {
            return name;
        }

        /**
         * Returns the value of the suggestion.
         *
         * @return the value of the suggestion
         */
        public String getValue() {
            return value;
        }

        /**
         * Returns the type of the suggestion.
         *
         * @return the type of the suggestion
         */
        public String getType() {
            return type;
        }

        /**
         * Returns the css of the suggestion.
         *
         * @return the css of the suggestion
         */
        public String getCss() {
            return css;
        }
    }

    /**
     * Parses all suggestions which have been submitted by the frontend.
     *
     * @param ctx the current request
     * @return the parsed suggestion wrapped as magic search instance
     */
    @SuppressWarnings("unchecked")
    public static MagicSearch parseSuggestions(WebContext ctx) {
        MagicSearch ms = new MagicSearch();
        ms.suggestions = Lists.newArrayList();
        if (!ctx.get("suggestions").isEmptyString()) {
            try {
                JSONArray suggestions = JSON.parseArray(ctx.get("suggestions").asString());
                if (suggestions != null) {
                    for (Object obj : suggestions) {
                        if (obj instanceof String) {
                            Suggestion suggestion =
                                    new Suggestion((String) obj).withType(TYPE_QUERY).withCSS("suggestion-query");
                            ms.suggestions.add(suggestion);
                        } else if (obj instanceof JSONObject) {
                            JSONObject json = (JSONObject) obj;
                            Suggestion suggestion =
                                    new Suggestion(json.getString("name")).withValue(json.getString("value"))
                                                                          .withType(json.getString("type"))
                                                                          .withCSS(json.getString("css"));
                            ms.suggestions.add(suggestion);
                        }
                    }
                }
            } catch (JSONException e) {
                Exceptions.ignore(e);
            }
        }

        return ms;
    }

    /**
     * Returns the list of parsed suggestions.
     *
     * @return all parsed suggestions
     */
    public List<Suggestion> getSuggestions() {
        return suggestions;
    }

    /**
     * Uses the suggestions of type {@link #TYPE_QUERY} and applies them as search query in the given fields for the
     * given smart query.
     * ^
     *
     * @param qry    the query to enhance
     * @param fields the fields to search in
     */
    public void applyQueries(SmartQuery<? extends Entity> qry, Column... fields) {
        for (Suggestion suggestion : suggestions) {
            if (TYPE_QUERY.equals(suggestion.getType())) {
                qry.where(Like.allWordsInAnyField(suggestion.getValue(), fields));
            }
        }
    }

    /**
     * Computes a JSON object representing all suggestions which is used to initialize the magic search
     * after a page load.
     *
     * @return a JSON string representing the currently selected suggestions
     */
    public String getSuggestionsString() {
        if (suggestions == null || suggestions.isEmpty()) {
            return "[]";
        }
        JSONArray result = new JSONArray();
        for (Suggestion suggestion : suggestions) {
            JSONObject json = new JSONObject();
            json.put("name", suggestion.getName());
            JSONObject value = new JSONObject();
            value.put("name", suggestion.getName());
            value.put("value", suggestion.getValue());
            value.put("type", suggestion.getType());
            value.put("css", suggestion.getCss());
            json.put("value", value);
            result.add(json);
        }
        return result.toJSONString();
    }

    /**
     * Called to generate completions for a given query.
     */
    public interface SuggestionComputer {
        void search(String query, Consumer<Suggestion> result);
    }

    /**
     * Handles the given request and generates the appropriate JSON as expected by the select2 binding.
     *
     * @param ctx    the request to handle
     * @param search the handler to generate suggestions
     */
    public static void generateSuggestions(WebContext ctx, SuggestionComputer search) {
        StructuredOutput out = ctx.respondWith().json();
        out.beginResult();
        out.beginArray("results");
        search.search(ctx.get("query").asString(), s -> {
            out.beginObject("result");
            out.property("name", s.getName());
            out.beginObject("value");
            out.property("type", s.getType());
            out.property("name", s.getName());
            out.property("value", s.getValue());
            out.property("css", s.getCss());
            out.endObject();
            out.endObject();
        });
        out.endArray();
        out.endResult();
    }
}
