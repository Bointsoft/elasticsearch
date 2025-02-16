/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0 and the Server Side Public License, v 1; you may not use this file except
 * in compliance with, at your election, the Elastic License 2.0 or the Server
 * Side Public License, v 1.
 */

package org.elasticsearch.qa.custom_logging;

import org.elasticsearch.core.SuppressForbidden;
import org.elasticsearch.test.hamcrest.RegexMatcher;
import org.elasticsearch.test.rest.ESRestTestCase;
import org.hamcrest.Matchers;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.List;

/**
 * This test verifies that Elasticsearch can startup successfully with a custom logging config using variables introduced in
 * <code>ESJsonLayout</code>
 * The intention is to confirm that users can still run their Elasticsearch instances with previous configurations.
 */
public class CustomLoggingConfigIT extends ESRestTestCase {
    private static final String NODE_STARTED = ".*integTest-0.*cluster.uuid.*node.id.*recovered.*cluster_state.*";

    public void testSuccessfulStartupWithCustomConfig() throws Exception {
        assertBusy(() -> {
            List<String> lines = readAllLines(getLogFile());
            assertThat(lines, Matchers.hasItem(RegexMatcher.matches(NODE_STARTED)));
        });
    }

    private List<String> readAllLines(Path logFile) {
        return AccessController.doPrivileged((PrivilegedAction<List<String>>) () -> {
            try {
                return Files.readAllLines(logFile, StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
    }

    @SuppressForbidden(reason = "PathUtils doesn't have permission to read this file")
    private Path getLogFile() {
        String logFileString = System.getProperty("tests.logfile");
        if (logFileString == null) {
            fail("tests.logfile must be set to run this test. It is automatically "
                + "set by gradle. If you must set it yourself then it should be the absolute path to the "
                + "log file.");
        }
        return Paths.get(logFileString);
    }
}
