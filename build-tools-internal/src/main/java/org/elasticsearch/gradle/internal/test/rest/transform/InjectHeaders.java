/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0 and the Server Side Public License, v 1; you may not use this file except
 * in compliance with, at your election, the Elastic License 2.0 or the Server
 * Side Public License, v 1.
 */

package org.elasticsearch.gradle.internal.test.rest.transform;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;

import javax.annotation.Nullable;
import java.util.Iterator;
import java.util.Map;

/**
 * A {@link RestTestTransform} that injects HTTP headers into a REST test. This includes adding the necessary values to the "do" section
 * as well as adding headers as a features to the "setup" and "teardown" sections.
 */
public class InjectHeaders implements RestTestTransformByObjectKey, RestTestTransformGlobalSetup, RestTestTransformGlobalTeardown {

    private static JsonNodeFactory jsonNodeFactory = JsonNodeFactory.withExactBigDecimals(false);

    private final Map<String, String> headers;

    /**
     * @param headers The headers to inject
     */
    public InjectHeaders(Map<String, String> headers) {
        this.headers = headers;
    }

    @Override
    public void transformTest(ObjectNode doNodeParent) {
        ObjectNode doNodeValue = (ObjectNode) doNodeParent.get(getKeyToFind());
        ObjectNode headersNode = (ObjectNode) doNodeValue.get("headers");
        if (headersNode == null) {
            headersNode = new ObjectNode(jsonNodeFactory);
        }
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            headersNode.set(entry.getKey(), TextNode.valueOf(entry.getValue()));
        }
        doNodeValue.set("headers", headersNode);
    }

    @Override
    public String getKeyToFind() {
        return "do";
    }

    @Override
    public ObjectNode transformSetup(ObjectNode setupNodeParent) {
        // check to ensure that headers feature does not already exist
        if (setupNodeParent != null) {
            ArrayNode setupNode = (ArrayNode) setupNodeParent.get("setup");
            if (hasHeadersFeature(setupNode)) {
                return setupNodeParent;
            }
        }
        // transform or insert the headers feature into setup/skip/features
        ArrayNode setupNode;
        if (setupNodeParent == null) {
            setupNodeParent = new ObjectNode(jsonNodeFactory);
            setupNode = new ArrayNode(jsonNodeFactory);
            setupNodeParent.set("setup", setupNode);
        }
        setupNode = (ArrayNode) setupNodeParent.get("setup");
        addSkip(setupNode);
        return setupNodeParent;
    }

    @Override
    public ObjectNode transformTeardown(@Nullable ObjectNode teardownNodeParent) {
        if (teardownNodeParent != null) {
            ArrayNode teardownNode = (ArrayNode) teardownNodeParent.get("teardown");
            // only transform an existing teardown section since a teardown does not inherit from setup but still needs the skip section
            if (teardownNode != null) {
                // check to ensure that headers feature does not already exist
                if (hasHeadersFeature(teardownNode)) {
                    return teardownNodeParent;
                }
                addSkip(teardownNode);
                return teardownNodeParent;
            }
        }
        return teardownNodeParent;
    }

    private boolean hasHeadersFeature(ArrayNode skipParent) {
        JsonNode features = skipParent.at("/0/skip/features");
        if (features != null) {
            if (features.isArray()) {
                ArrayNode featuresArray = (ArrayNode) features;
                Iterator<JsonNode> it = featuresArray.elements();
                while (it.hasNext()) {
                    if ("headers".equals(it.next().asText())) {
                        return true;
                    }
                }
            } else {
                if ("headers".equals(features.asText())) {
                    return true;
                }
            }
        }
        return false;
    }

    private void addSkip(ArrayNode skipParent) {
        Iterator<JsonNode> skipParentIt = skipParent.elements();
        boolean foundSkipNode = false;
        while (skipParentIt.hasNext()) {
            JsonNode arrayEntry = skipParentIt.next();
            if (arrayEntry.isObject()) {
                ObjectNode skipCandidate = (ObjectNode) arrayEntry;
                if (skipCandidate.get("skip") != null) {
                    ObjectNode skipNode = (ObjectNode) skipCandidate.get("skip");
                    foundSkipNode = true;
                    JsonNode featuresNode = skipNode.get("features");
                    if (featuresNode == null) {
                        ObjectNode featuresNodeObject = new ObjectNode(jsonNodeFactory);
                        skipNode.set("features", TextNode.valueOf("headers"));
                    } else if (featuresNode.isArray()) {
                        ArrayNode featuresNodeArray = (ArrayNode) featuresNode;
                        featuresNodeArray.add("headers");
                    } else if (featuresNode.isTextual()) {
                        // convert to an array
                        ArrayNode featuresNodeArray = new ArrayNode(jsonNodeFactory);
                        featuresNodeArray.add(featuresNode.asText());
                        featuresNodeArray.add("headers");
                        // overwrite the features object
                        skipNode.set("features", featuresNodeArray);
                    }
                }
            }
        }
        if (foundSkipNode == false) {
            ObjectNode skipNode = new ObjectNode(jsonNodeFactory);
            ObjectNode featuresNode = new ObjectNode(jsonNodeFactory);
            skipParent.insert(0, skipNode);
            featuresNode.set("features", TextNode.valueOf("headers"));
            skipNode.set("skip", featuresNode);
        }
    }
}