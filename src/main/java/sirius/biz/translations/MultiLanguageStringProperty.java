/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.translations;

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.bson.Document;
import sirius.biz.web.ComplexLoadProperty;
import sirius.db.es.ESPropertyInfo;
import sirius.db.es.IndexMappings;
import sirius.db.es.annotations.IndexMode;
import sirius.db.jdbc.schema.SQLPropertyInfo;
import sirius.db.jdbc.schema.Table;
import sirius.db.jdbc.schema.TableColumn;
import sirius.db.mixing.AccessPath;
import sirius.db.mixing.BaseEntity;
import sirius.db.mixing.EntityDescriptor;
import sirius.db.mixing.InvalidFieldException;
import sirius.db.mixing.Mixing;
import sirius.db.mixing.Property;
import sirius.db.mixing.PropertyFactory;
import sirius.db.mixing.properties.BaseMapProperty;
import sirius.kernel.commons.Json;
import sirius.kernel.commons.Strings;
import sirius.kernel.commons.Tuple;
import sirius.kernel.commons.Value;
import sirius.kernel.commons.Values;
import sirius.kernel.di.std.Register;
import sirius.kernel.health.Exceptions;
import sirius.kernel.nls.NLS;
import sirius.web.http.WebContext;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.sql.Types;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Represents a {@link MultiLanguageString} field in a {@link sirius.db.mongo.MongoEntity}.
 * <p>
 * Multi-Language Strings are stored as a list of nested objects which contain a
 * <tt>lang</tt> and a <tt>text</tt> property.
 * <p>
 * For JDBC/SQL databases the map of translations is serialized into a JSON representation within the field. Note
 * that this is only done, if more than just the "fallback" is filled. Note however, that as soon as multi language
 * support is enabled (or rather not suppressed via {@link MultiLanguageString#withConditionName(String)}) each column
 * will be turned into a <tt>TEXT</tt> field, rather than a <tt>CHAR</tt>, even if a length is given.
 */
public class MultiLanguageStringProperty extends BaseMapProperty
        implements SQLPropertyInfo, ESPropertyInfo, ComplexLoadProperty {

    private static final String LANGUAGE_PROPERTY = "lang";
    private static final String TEXT_PROPERTY = "text";
    private static final String PARAM_FIELD = "field";
    private static final String I18N_MAP_SEPARATOR = "$$MAP:";

    /**
     * Factory for generating properties based on their field type
     */
    @Register
    public static class Factory implements PropertyFactory {

        @Override
        public boolean accepts(EntityDescriptor descriptor, Field field) {
            return MultiLanguageString.class.equals(field.getType());
        }

        @Override
        public void create(EntityDescriptor descriptor,
                           AccessPath accessPath,
                           Field field,
                           Consumer<Property> propertyConsumer) {
            if (!Modifier.isFinal(field.getModifiers())) {
                Mixing.LOG.WARN("Field %s in %s is not final! This will probably result in errors.",
                                field.getName(),
                                field.getDeclaringClass().getName());
            }

            propertyConsumer.accept(new MultiLanguageStringProperty(descriptor, accessPath, field));
        }
    }

    private boolean i18nEnabled;

    MultiLanguageStringProperty(EntityDescriptor descriptor, AccessPath accessPath, Field field) {
        super(descriptor, accessPath, field);
    }

    @Override
    protected void link() {
        super.link();
        this.i18nEnabled = getMultiLanguageString(getDescriptor().getReferenceInstance()).isConditionEnabled();
    }

    @Override
    protected void checkNullability(Object propertyValue) {
        // Intentionally left empty as logic is implemented in onBeforeSaveChecks
    }

    @Override
    protected void onBeforeSaveChecks(Object entity) {
        MultiLanguageString multiLanguageString = getMultiLanguageString(entity);

        if (this.length > 0) {
            multiLanguageString.data()
                               .values()
                               .stream()
                               .filter(value -> Strings.isFilled(value) && value.length() > this.length)
                               .findFirst()
                               .ifPresent(value -> {
                                   throw Exceptions.createHandled()
                                                   .withNLSKey("StringProperty.dataTruncation")
                                                   .set("value", Strings.limit(value, 30))
                                                   .set(PARAM_FIELD, this.getFullLabel())
                                                   .set("length", value.length())
                                                   .set("maxLength", this.length)
                                                   .handle();
                               });
        }

        if (isNullable()) {
            return;
        }

        if (multiLanguageString.isWithFallback()) {
            String fallbackValue = multiLanguageString.data().get(MultiLanguageString.FALLBACK_KEY);
            if (Strings.isEmpty(fallbackValue)) {
                throw Exceptions.createHandled()
                                .error(new InvalidFieldException(getName()))
                                .withNLSKey("MultiLanguageStringProperty.fallbackNotSet")
                                .set(PARAM_FIELD, getFullLabel())
                                .handle();
            }
        } else {
            multiLanguageString.data()
                               .entrySet()
                               .stream()
                               .filter(entry -> Strings.isEmpty(entry.getValue()))
                               .findAny()
                               .ifPresent(entry -> {
                                   throw Exceptions.createHandled()
                                                   .error(new InvalidFieldException(getName()))
                                                   .withNLSKey("MultiLanguageStringProperty.empty")
                                                   .set(PARAM_FIELD, getFullLabel())
                                                   .handle();
                               });
        }
    }

    @Override
    protected void onValidate(Object entity, Consumer<String> validationConsumer) {
        MultiLanguageString multiLanguageString = getMultiLanguageString(entity);

        if (!multiLanguageString.isWithFallback() || multiLanguageString.data().isEmpty()) {
            return;
        }

        if (Strings.isEmpty(multiLanguageString.fetchText(MultiLanguageString.FALLBACK_KEY))
            && multiLanguageString.data()
                                  .entrySet()
                                  .stream()
                                  .filter(entry -> !entry.getKey().equals(MultiLanguageString.FALLBACK_KEY))
                                  .anyMatch(entry -> Strings.isFilled(entry.getValue()))) {
            validationConsumer.accept(NLS.fmtr("MultiLanguageStringProperty.fallbackNotSet")
                                         .set(PARAM_FIELD, getFullLabel())
                                         .format());
        }
    }

    /**
     * Bypasses {@link BaseMapProperty#getValueFromField(Object)} to access the actual MultiLanguageString entity.
     *
     * @param target the target object determined by the access path
     * @return the corresponding {@link MultiLanguageString} object determined by the access path
     * @see Property#getValueFromField(Object)
     */
    public MultiLanguageString getMultiLanguageString(Object target) {
        try {
            return (MultiLanguageString) field.get(accessPath.apply(target));
        } catch (IllegalAccessException e) {
            throw Exceptions.handle()
                            .to(Mixing.LOG)
                            .error(e)
                            .withSystemErrorMessage("Cannot read property '%s' (from '%s'): %s (%s)",
                                                    getName(),
                                                    getDefinition())
                            .handle();
        }
    }

    /**
     * Loads a value from a MongoDB datasource into a {@link MultiLanguageStringProperty} and skips language entries with null values.
     *
     * @param object the database value
     * @return the value which can be stored in the associated {@link MultiLanguageStringProperty} field
     */
    @Override
    @SuppressWarnings("unchecked")
    protected Object transformFromMongo(Value object) {
        Map<String, String> texts = new LinkedHashMap<>();
        Object valueObject = object.get();
        if (valueObject instanceof String) {
            texts.put(MultiLanguageString.FALLBACK_KEY, valueObject.toString());
        } else if (valueObject instanceof List) {
            for (Document document : (List<Document>) valueObject) {
                Object textValue = document.get(TEXT_PROPERTY);
                if (textValue != null) {
                    texts.put(document.get(LANGUAGE_PROPERTY).toString(), textValue.toString());
                }
            }
        }
        return texts;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected Object transformToMongo(Object object) {
        List<Document> texts = new ArrayList<>();
        ((Map<String, String>) object).forEach((language, text) -> {
            Document doc = new Document();
            doc.put(LANGUAGE_PROPERTY, language);
            doc.put(TEXT_PROPERTY, text);
            texts.add(doc);
        });
        return texts;
    }

    @Override
    protected Object transformFromElastic(Value object) {
        Map<String, String> result = new HashMap<>();
        Object value = object.get();
        if (value instanceof LinkedHashMap<?, ?> map) {
            map.forEach((key, text) -> {
                if (key instanceof String language && text instanceof String string) {
                    result.put(language, string);
                }
            });
        }
        return result;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected Object transformToElastic(Object object) {
        return Json.convertFromMap((Map<String, String>) object);
    }

    @Override
    protected Object transformFromJDBC(Value object) {
        String rawData = object.asString();
        Tuple<String, String> fallbackAndMap = Strings.split(rawData, I18N_MAP_SEPARATOR);

        Map<String, String> texts = new LinkedHashMap<>();
        if (Strings.isFilled(fallbackAndMap.getFirst())) {
            texts.put(MultiLanguageString.FALLBACK_KEY, fallbackAndMap.getFirst());
        }

        if (Strings.isFilled(fallbackAndMap.getSecond())) {
            Json.parseObject(fallbackAndMap.getSecond())
                .properties()
                .forEach(entry -> texts.put(entry.getKey(), entry.getValue().asText()));
        }

        return texts;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected Object transformToJDBC(Object object) {
        Map<String, String> texts = (Map<String, String>) object;
        if (texts.isEmpty()) {
            return "";
        }

        StringBuilder data = new StringBuilder(texts.getOrDefault(MultiLanguageString.FALLBACK_KEY, ""));
        if (i18nEnabled && (texts.size() > 1 || !texts.containsKey(MultiLanguageString.FALLBACK_KEY))) {
            ObjectNode textMap = Json.createObject();
            texts.entrySet()
                 .stream()
                 .filter(entry -> !MultiLanguageString.FALLBACK_KEY.equals(entry.getKey()))
                 .forEach(entry -> textMap.put(entry.getKey(), entry.getValue()));
            data.append(I18N_MAP_SEPARATOR);
            data.append(Json.write(textMap));
        }

        return data.toString();
    }

    @Override
    public void contributeToTable(Table table) {
        table.getColumns().add(new TableColumn(this, i18nEnabled ? Types.CLOB : Types.CHAR));
    }

    @Override
    public void describeProperty(ObjectNode description) {
        description.put(IndexMappings.MAPPING_TYPE, "object");
        description.put(IndexMappings.MAPPING_DYNAMIC, true);
        transferOption(IndexMappings.MAPPING_ENABLED, getAnnotation(IndexMode.class), IndexMode::indexed, description);
    }

    @Override
    public boolean doesEnableDynamicMappings() {
        return true;
    }

    @Override
    protected Object transformValueFromImport(Value value) {
        MultiLanguageString multiLanguageString = (MultiLanguageString) super.transformValueFromImport(value);
        if (i18nEnabled && multiLanguageString.isEnabledForCurrentUser() && multiLanguageString.data()
                                                                                               .keySet()
                                                                                               .stream()
                                                                                               .anyMatch(key -> !isValidLanguageCode(
                                                                                                       multiLanguageString,
                                                                                                       key))) {
            MultiLanguageString onlyValidEntries = new MultiLanguageString();
            if (multiLanguageString.isWithFallback()) {
                onlyValidEntries.withFallback();
                onlyValidEntries.setFallback(multiLanguageString.getFallback());
            }
            multiLanguageString.data()
                               .entrySet()
                               .stream()
                               .filter(entry -> isValidLanguageCode(multiLanguageString, entry.getKey()))
                               .forEach(entry -> onlyValidEntries.addText(entry.getKey(), entry.getValue()));
            if (onlyValidEntries.isEmpty() && multiLanguageString.isFilled()) {
                throw illegalFieldValue(value);
            }
            return onlyValidEntries;
        }
        return multiLanguageString;
    }

    @Override
    public boolean loadFromWebContext(WebContext webContext, BaseEntity<?> entity) {
        MultiLanguageString multiLanguageString = getMultiLanguageString(entity);
        if (multiLanguageString.isWithFallback() && webContext.hasParameter(getPropertyName())) {
            multiLanguageString.setFallback(webContext.getParameter(getPropertyName()));
        }

        if (i18nEnabled && multiLanguageString.isEnabledForCurrentUser()) {
            webContext.getParameterNames()
                      .stream()
                      .filter(parameter -> parameter.startsWith(getPropertyName() + "-"))
                      .forEach(parameter -> {
                          String code = Strings.split(parameter, "-").getSecond();
                          if (isValidLanguageCode(multiLanguageString, code)) {
                              multiLanguageString.addText(code, webContext.getParameter(parameter));
                          }
                      });
        }

        return true;
    }

    /**
     * Ensures that the given code is a valid (and accepted) language code.
     * <p>
     * This is mainly to either enforce the valid languages as set in the MLS property or to protect against
     * malicious data in a WebContext. We therefore check if we know the given code either as it is a known
     * language of the system or if it occurs in the list of all known languages. We perform both checks as
     * the latter requires a properly populated lookup table, which might not be available on all systems. The
     * known languages however are maintained via the system config and are always populated.
     *
     * @param multiLanguageString the property to check for valid languages
     * @param code                the language code to check
     * @return <tt>true</tt> if the code is accepted, <tt>false</tt> otherwise
     */
    private boolean isValidLanguageCode(MultiLanguageString multiLanguageString, String code) {
        return multiLanguageString.getValidLanguages().contains(code);
    }

    @Override
    public boolean shouldAutoload(WebContext webContext) {
        // As an MLS field might not have a default value, we have to inspect all
        // parameters instead of just the one with the proper name...
        return webContext.getParameterNames().stream().anyMatch(parameter -> parameter.startsWith(getPropertyName()));
    }

    @Override
    public void parseValues(Object entity, Values values) {
        MultiLanguageString multiLanguageString = getMultiLanguageString(entity);
        multiLanguageString.setFallback(values.at(0).getString());
    }

    @Override
    public String getColumnDefaultValue() {
        // not supported
        return null;
    }
}
