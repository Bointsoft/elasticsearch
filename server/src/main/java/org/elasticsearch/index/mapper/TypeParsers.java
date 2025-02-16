/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0 and the Server Side Public License, v 1; you may not use this file except
 * in compliance with, at your election, the Elastic License 2.0 or the Server
 * Side Public License, v 1.
 */

package org.elasticsearch.index.mapper;

import org.elasticsearch.common.logging.DeprecationCategory;
import org.elasticsearch.common.logging.DeprecationLogger;
import org.elasticsearch.common.time.DateFormatter;
import org.elasticsearch.index.similarity.SimilarityProvider;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Consumer;

import static org.elasticsearch.common.xcontent.support.XContentMapValues.isArray;
import static org.elasticsearch.common.xcontent.support.XContentMapValues.nodeStringValue;

public class TypeParsers {
    private static final DeprecationLogger deprecationLogger = DeprecationLogger.getLogger(TypeParsers.class);

    public static final String DOC_VALUES = "doc_values";
    public static final String INDEX_OPTIONS_DOCS = "docs";
    public static final String INDEX_OPTIONS_FREQS = "freqs";
    public static final String INDEX_OPTIONS_POSITIONS = "positions";
    public static final String INDEX_OPTIONS_OFFSETS = "offsets";

    public static void checkNull(String propName, Object propNode) {
        if (false == propName.equals("null_value") && propNode == null) {
            /*
             * No properties *except* null_value are allowed to have null. So we catch it here and tell the user something useful rather
             * than send them a null pointer exception later.
             */
            throw new MapperParsingException("[" + propName + "] must not have a [null] value");
        }
    }

    /**
     * Parse the {@code meta} key of the mapping.
     */
    public static Map<String, String> parseMeta(String name, Object metaObject) {
        if (metaObject instanceof Map == false) {
            throw new MapperParsingException("[meta] must be an object, got " + metaObject.getClass().getSimpleName() +
                "[" + metaObject + "] for field [" + name + "]");
        }
        @SuppressWarnings("unchecked")
        Map<String, ?> meta = (Map<String, ?>) metaObject;
        if (meta.size() > 5) {
            throw new MapperParsingException("[meta] can't have more than 5 entries, but got " + meta.size() + " on field [" +
                name + "]");
        }
        for (String key : meta.keySet()) {
            if (key.codePointCount(0, key.length()) > 20) {
                throw new MapperParsingException("[meta] keys can't be longer than 20 chars, but got [" + key +
                    "] for field [" + name + "]");
            }
        }
        for (Object value : meta.values()) {
            if (value instanceof String) {
                String sValue = (String) value;
                if (sValue.codePointCount(0, sValue.length()) > 50) {
                    throw new MapperParsingException("[meta] values can't be longer than 50 chars, but got [" + value +
                        "] for field [" + name + "]");
                }
            } else if (value == null) {
                throw new MapperParsingException("[meta] values can't be null (field [" + name + "])");
            } else {
                throw new MapperParsingException("[meta] values can only be strings, but got " +
                    value.getClass().getSimpleName() + "[" + value + "] for field [" + name + "]");
            }
        }
        Map<String, String> sortedMeta = new TreeMap<>();
        for (Map.Entry<String, ?> entry : meta.entrySet()) {
            sortedMeta.put(entry.getKey(), (String) entry.getValue());
        }
        return Collections.unmodifiableMap(sortedMeta);
    }

    @SuppressWarnings({"unchecked"})
    public static boolean parseMultiField(Consumer<FieldMapper.Builder> multiFieldsBuilder, String name,
                                          MappingParserContext parserContext, String propName, Object propNode) {
        if (propName.equals("fields")) {
            if (parserContext.isWithinMultiField()) {
                deprecationLogger.deprecate(DeprecationCategory.MAPPINGS, "multifield_within_multifield",
                    "At least one multi-field, [" + name + "], was " +
                    "encountered that itself contains a multi-field. Defining multi-fields within a multi-field is deprecated and will " +
                    "no longer be supported in 8.0. To resolve the issue, all instances of [fields] that occur within a [fields] block " +
                    "should be removed from the mappings, either by flattening the chained [fields] blocks into a single level, or " +
                    "switching to [copy_to] if appropriate.");
            }

            parserContext = parserContext.createMultiFieldContext(parserContext);

            final Map<String, Object> multiFieldsPropNodes;
            if (propNode instanceof List && ((List<?>) propNode).isEmpty()) {
                multiFieldsPropNodes = Collections.emptyMap();
            } else if (propNode instanceof Map) {
                multiFieldsPropNodes = (Map<String, Object>) propNode;
            } else {
                throw new MapperParsingException("expected map for property [fields] on field [" + propNode + "] or " +
                    "[" + propName + "] but got a " + propNode.getClass());
            }

            for (Map.Entry<String, Object> multiFieldEntry : multiFieldsPropNodes.entrySet()) {
                String multiFieldName = multiFieldEntry.getKey();
                if (multiFieldName.contains(".")) {
                    throw new MapperParsingException("Field name [" + multiFieldName + "] which is a multi field of [" + name + "] cannot" +
                        " contain '.'");
                }
                if ((multiFieldEntry.getValue() instanceof Map) == false) {
                    throw new MapperParsingException("illegal field [" + multiFieldName + "], only fields can be specified inside fields");
                }
                @SuppressWarnings("unchecked")
                Map<String, Object> multiFieldNodes = (Map<String, Object>) multiFieldEntry.getValue();

                String type;
                Object typeNode = multiFieldNodes.get("type");
                if (typeNode != null) {
                    type = typeNode.toString();
                } else {
                    throw new MapperParsingException("no type specified for property [" + multiFieldName + "]");
                }

                Mapper.TypeParser typeParser = parserContext.typeParser(type);
                if (typeParser == null) {
                    throw new MapperParsingException("no handler for type [" + type + "] declared on field [" + multiFieldName + "]");
                }
                if (typeParser instanceof FieldMapper.TypeParser == false) {
                    throw new MapperParsingException("Type [" + type + "] cannot be used in multi field");
                }

                FieldMapper.TypeParser fieldTypeParser = (FieldMapper.TypeParser) typeParser;
                multiFieldsBuilder.accept(fieldTypeParser.parse(multiFieldName, multiFieldNodes, parserContext));
                multiFieldNodes.remove("type");
                MappingParser.checkNoRemainingFields(propName, multiFieldNodes);
            }
            return true;
        }
        return false;
    }

    public static DateFormatter parseDateTimeFormatter(Object node) {
        if (node instanceof String) {
            return DateFormatter.forPattern((String) node);
        }
        throw new IllegalArgumentException("Invalid format: [" + node.toString() + "]: expected string value");
    }

    @SuppressWarnings("unchecked")
    public static List<String> parseCopyFields(Object propNode) {
        List<String> copyFields = new ArrayList<>();
        if (isArray(propNode)) {
            for (Object node : (List<Object>) propNode) {
                copyFields.add(nodeStringValue(node, null));
            }
        } else {
            copyFields.add(nodeStringValue(propNode, null));
        }
        return copyFields;
    }

    public static SimilarityProvider resolveSimilarity(MappingParserContext parserContext, String name, Object value) {
        if (value == null) {
            return null;    // use default
        }
        SimilarityProvider similarityProvider = parserContext.getSimilarity(value.toString());
        if (similarityProvider == null) {
            throw new MapperParsingException("Unknown Similarity type [" + value + "] for field [" + name + "]");
        }
        return similarityProvider;
    }
}
