/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0; you may not use this file except in compliance with the Elastic License
 * 2.0.
 */
package org.elasticsearch.xpack.analytics.stringstats;

import org.apache.lucene.document.Field;
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.RandomIndexWriter;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermInSetQuery;
import org.apache.lucene.store.Directory;
import org.apache.lucene.util.BytesRef;
import org.elasticsearch.core.CheckedConsumer;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.index.mapper.IpFieldMapper;
import org.elasticsearch.index.mapper.MappedFieldType;
import org.elasticsearch.index.mapper.NumberFieldMapper;
import org.elasticsearch.index.mapper.TextFieldMapper;
import org.elasticsearch.plugins.SearchPlugin;
import org.elasticsearch.script.MockScriptEngine;
import org.elasticsearch.script.Script;
import org.elasticsearch.script.ScriptEngine;
import org.elasticsearch.script.ScriptModule;
import org.elasticsearch.script.ScriptService;
import org.elasticsearch.script.ScriptType;
import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.aggregations.AggregatorTestCase;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;
import org.elasticsearch.search.aggregations.bucket.terms.TermsAggregationBuilder;
import org.elasticsearch.search.aggregations.bucket.terms.TermsAggregator;
import org.elasticsearch.search.aggregations.support.CoreValuesSourceType;
import org.elasticsearch.search.aggregations.support.ValueType;
import org.elasticsearch.search.aggregations.support.ValuesSourceType;
import org.elasticsearch.search.lookup.LeafDocLookup;
import org.elasticsearch.xpack.analytics.AnalyticsPlugin;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

import static java.util.Collections.emptyMap;
import static java.util.Collections.emptySet;
import static java.util.Collections.singleton;
import static java.util.Collections.singletonList;
import static java.util.Collections.singletonMap;

public class StringStatsAggregatorTests extends AggregatorTestCase {

    @Override
    protected List<SearchPlugin> getSearchPlugins() {
        return Arrays.asList(new AnalyticsPlugin(Settings.EMPTY));
    }

    private static final String VALUE_SCRIPT_NAME = "value_script";
    private static final String FIELD_SCRIPT_NAME = "field_script";

    public void testNoDocs() throws IOException {
        testAggregation(new MatchAllDocsQuery(), iw -> {
            // Intentionally not writing any docs
        }, stats -> {
            assertEquals(0, stats.getCount());
            assertEquals(Integer.MIN_VALUE, stats.getMaxLength());
            assertEquals(Integer.MAX_VALUE, stats.getMinLength());
            assertEquals(Double.NaN, stats.getAvgLength(), 0);
            assertTrue(stats.getDistribution().isEmpty());
            assertEquals(0.0, stats.getEntropy(), 0);
        });
    }

    public void testUnmappedField() throws IOException {
        StringStatsAggregationBuilder aggregationBuilder = new StringStatsAggregationBuilder("_name").field("text");
        testAggregation(aggregationBuilder, new MatchAllDocsQuery(), iw -> {
            for(int i = 0; i < 10; i++) {
                iw.addDocument(singleton(new TextField("text", "test" + i, Field.Store.NO)));
            }
        },  stats -> {
            assertEquals(0, stats.getCount());
            assertEquals(Integer.MIN_VALUE, stats.getMaxLength());
            assertEquals(Integer.MAX_VALUE, stats.getMinLength());
            assertEquals(Double.NaN, stats.getAvgLength(), 0);
            assertTrue(stats.getDistribution().isEmpty());
            assertEquals(0.0, stats.getEntropy(), 0);

        });
    }

    public void testUnmappedWithMissingField() throws IOException {
        StringStatsAggregationBuilder aggregationBuilder = new StringStatsAggregationBuilder("_name")
            .field("text")
            .missing("abca");
        testAggregation(aggregationBuilder, new MatchAllDocsQuery(), iw -> {
            for(int i=0; i < 10; i++) {
                iw.addDocument(singleton(new TextField("text", "test" + i, Field.Store.NO)));
            }
        }, stats -> {
            assertEquals(10, stats.getCount());
            assertEquals(4, stats.getMaxLength());
            assertEquals(4, stats.getMinLength());
            assertEquals(4.0, stats.getAvgLength(), 0);
            assertEquals(3, stats.getDistribution().size());
            assertEquals(0.50, stats.getDistribution().get("a"), 0);
            assertEquals(0.25, stats.getDistribution().get("b"), 0);
            assertEquals(0.25, stats.getDistribution().get("c"), 0);
            assertEquals(1.5, stats.getEntropy(), 0);
        });
    }

    public void testMissing() throws IOException {
        final TextFieldMapper.TextFieldType fieldType = new TextFieldMapper.TextFieldType("text");
        fieldType.setFielddata(true);

        final StringStatsAggregationBuilder aggregationBuilder = new StringStatsAggregationBuilder("_name")
            .field(fieldType.name())
            .missing("b");

        testAggregation(aggregationBuilder, new MatchAllDocsQuery(), iw -> {
            iw.addDocument(singleton(new TextField(fieldType.name(), "a", Field.Store.NO)));
            iw.addDocument(emptySet());
            iw.addDocument(singleton(new TextField(fieldType.name(), "a", Field.Store.NO)));
            iw.addDocument(emptySet());
        }, stats -> {
            assertEquals(4, stats.getCount());
            assertEquals(1, stats.getMaxLength());
            assertEquals(1, stats.getMinLength());
            assertEquals(1.0, stats.getAvgLength(), 0);
            assertEquals(2, stats.getDistribution().size());
            assertEquals(0.5, stats.getDistribution().get("a"), 0);
            assertEquals(0.5, stats.getDistribution().get("b"), 0);
            assertEquals(1.0, stats.getEntropy(), 0);
        }, fieldType);
    }

    public void testSingleValuedField() throws IOException {
        testAggregation(new MatchAllDocsQuery(), iw -> {
            for(int i=0; i < 10; i++) {
                iw.addDocument(singleton(new TextField("text", "test" + i, Field.Store.NO)));
            }
        }, stats -> {
            assertEquals(10, stats.getCount());
            assertEquals(5, stats.getMaxLength());
            assertEquals(5, stats.getMinLength());
            assertEquals(5.0, stats.getAvgLength(), 0);
            assertEquals(13, stats.getDistribution().size());
            assertEquals(0.4, stats.getDistribution().get("t"), 0);
            assertEquals(0.2, stats.getDistribution().get("e"), 0);
            assertEquals(0.02, stats.getDistribution().get("0"), 0);
            assertEquals(2.58631, stats.getEntropy(), 0.00001);
        });
    }

    public void testNoMatchingField() throws IOException {
        testAggregation(new MatchAllDocsQuery(), iw -> {
            for(int i=0; i < 10; i++) {
                iw.addDocument(singleton(new TextField("wrong_field", "test" + i, Field.Store.NO)));
            }
        }, stats -> {
            assertEquals(0, stats.getCount());
            assertEquals(Integer.MIN_VALUE, stats.getMaxLength());
            assertEquals(Integer.MAX_VALUE, stats.getMinLength());
            assertEquals(Double.NaN, stats.getAvgLength(), 0);
            assertTrue(stats.getDistribution().isEmpty());
            assertEquals(0.0, stats.getEntropy(), 0);
        });
    }

    public void testQueryFiltering() throws IOException {
        testAggregation(new TermInSetQuery("text", new BytesRef("test0"), new BytesRef("test1")), iw -> {
            for(int i=0; i < 10; i++) {
                iw.addDocument(singleton(new TextField("text", "test" + i, Field.Store.NO)));
            }
        }, stats -> {
            assertEquals(2, stats.getCount());
            assertEquals(5, stats.getMaxLength());
            assertEquals(5, stats.getMinLength());
            assertEquals(5.0, stats.getAvgLength(), 0);
            assertEquals(5, stats.getDistribution().size());
            assertEquals(0.4, stats.getDistribution().get("t"), 0);
            assertEquals(0.2, stats.getDistribution().get("e"), 0);
            assertEquals(0.1, stats.getDistribution().get("0"), 0);
            assertEquals(2.12193, stats.getEntropy(), 0.00001);
        });
    }

    @AwaitsFix(bugUrl = "https://github.com/elastic/elasticsearch/issues/47469")
    public void testSingleValuedFieldWithFormatter() throws IOException {
        TextFieldMapper.TextFieldType fieldType = new TextFieldMapper.TextFieldType("text");
        fieldType.setFielddata(true);

        StringStatsAggregationBuilder aggregationBuilder = new StringStatsAggregationBuilder("_name")
            .field("text")
            .format("0000.00")
            .showDistribution(true);

        testAggregation(aggregationBuilder, new MatchAllDocsQuery(), iw -> {
            for(int i=0; i < 10; i++) {
                iw.addDocument(singleton(new TextField("text", "test" + i, Field.Store.NO)));
            }
        }, stats -> {
            assertEquals("0010.00", stats.getCountAsString());
            assertEquals("0005.00", stats.getMaxLengthAsString());
            assertEquals("0005.00", stats.getMinLengthAsString());
            assertEquals("0005.00", stats.getAvgLengthAsString());
            assertEquals("0002.58", stats.getEntropyAsString());
        }, fieldType);
    }

    /**
     * Test a string_stats aggregation as a subaggregation of a terms aggregation
     */
    public void testNestedAggregation() throws IOException {
        MappedFieldType numericFieldType = new NumberFieldMapper.NumberFieldType("value", NumberFieldMapper.NumberType.INTEGER);

        TextFieldMapper.TextFieldType textFieldType = new TextFieldMapper.TextFieldType("text");
        textFieldType.setFielddata(true);

        TermsAggregationBuilder aggregationBuilder = new TermsAggregationBuilder("terms")
            .userValueTypeHint(ValueType.NUMERIC)
            .field("value")
            .subAggregation(new StringStatsAggregationBuilder("text_stats").field("text").userValueTypeHint(ValueType.STRING));

        Directory directory = newDirectory();
        RandomIndexWriter indexWriter = new RandomIndexWriter(random(), directory);
        final int numDocs = 10;
        for (int i = 0; i < numDocs; i++) {
            for (int j = 0; j < 4; j++)
                indexWriter.addDocument(Arrays.asList(
                    new NumericDocValuesField("value", i + 1),
                    new TextField("text", "test" + j, Field.Store.NO))
                );
        }
        indexWriter.close();

        IndexReader indexReader = DirectoryReader.open(directory);
        IndexSearcher indexSearcher = newSearcher(indexReader, true, true);

        TermsAggregator aggregator = createAggregator(aggregationBuilder, indexSearcher, numericFieldType, textFieldType);
        aggregator.preCollection();
        indexSearcher.search(new MatchAllDocsQuery(), aggregator);
        aggregator.postCollection();

        Terms terms = (Terms) aggregator.buildTopLevel();
        assertNotNull(terms);
        List<? extends Terms.Bucket> buckets = terms.getBuckets();
        assertNotNull(buckets);
        assertEquals(10, buckets.size());

        for (int i = 0; i < 10; i++) {
            Terms.Bucket bucket = buckets.get(i);
            assertNotNull(bucket);
            assertEquals((long) i + 1, bucket.getKeyAsNumber());
            assertEquals(4L, bucket.getDocCount());

            InternalStringStats stats = bucket.getAggregations().get("text_stats");
            assertNotNull(stats);
            assertEquals(4L, stats.getCount());
            assertEquals(5, stats.getMaxLength());
            assertEquals(5, stats.getMinLength());
            assertEquals(5.0, stats.getAvgLength(), 0);
            assertEquals(7, stats.getDistribution().size());
            assertEquals(0.4, stats.getDistribution().get("t"), 0);
            assertEquals(0.2, stats.getDistribution().get("e"), 0);
            assertEquals(2.32193, stats.getEntropy(), 0.00001);
        }

        indexReader.close();
        directory.close();
    }

    public void testValueScriptSingleValuedField() throws IOException {
        final TextFieldMapper.TextFieldType fieldType = new TextFieldMapper.TextFieldType("text");
        fieldType.setFielddata(true);

        final StringStatsAggregationBuilder aggregationBuilder = new StringStatsAggregationBuilder("_name")
            .field(fieldType.name())
            .script(new Script(ScriptType.INLINE, MockScriptEngine.NAME, VALUE_SCRIPT_NAME, emptyMap()));

        testAggregation(aggregationBuilder, new MatchAllDocsQuery(), iw -> {
            iw.addDocument(singleton(new TextField(fieldType.name(), "b", Field.Store.NO)));
            iw.addDocument(singleton(new TextField(fieldType.name(), "b", Field.Store.NO)));
        }, stats -> {
            assertEquals(2, stats.getCount());
            assertEquals(2, stats.getMaxLength());
            assertEquals(2, stats.getMinLength());
            assertEquals(2.0, stats.getAvgLength(), 0);
            assertEquals(2, stats.getDistribution().size());
            assertEquals(0.5, stats.getDistribution().get("a"), 0);
            assertEquals(0.5, stats.getDistribution().get("b"), 0);
            assertEquals(1.0, stats.getEntropy(), 0);
        }, fieldType);
    }

    public void testValueScriptMultiValuedField() throws IOException {
        final TextFieldMapper.TextFieldType fieldType = new TextFieldMapper.TextFieldType("text");
        fieldType.setFielddata(true);

        final StringStatsAggregationBuilder aggregationBuilder = new StringStatsAggregationBuilder("_name")
            .field(fieldType.name())
            .script(new Script(ScriptType.INLINE, MockScriptEngine.NAME, VALUE_SCRIPT_NAME, emptyMap()));

        testAggregation(aggregationBuilder, new MatchAllDocsQuery(), iw -> {
            Set<TextField> FieldData = new java.util.HashSet<>();
            FieldData.add(new TextField(fieldType.name(), "b", Field.Store.NO));
            FieldData.add(new TextField(fieldType.name(), "c", Field.Store.NO));
            iw.addDocument(FieldData);
            Set<TextField> FieldData2 = new java.util.HashSet<>();
            FieldData2.add(new TextField(fieldType.name(), "b", Field.Store.NO));
            FieldData2.add(new TextField(fieldType.name(), "c", Field.Store.NO));
            iw.addDocument(FieldData2);
        }, stats -> {
            assertEquals(4, stats.getCount());
            assertEquals(2, stats.getMaxLength());
            assertEquals(2, stats.getMinLength());
            assertEquals(2.0, stats.getAvgLength(), 0);
            assertEquals(3, stats.getDistribution().size());
            assertEquals(0.5, stats.getDistribution().get("a"), 0);
            assertEquals(0.25, stats.getDistribution().get("b"), 0);
            assertEquals(0.25, stats.getDistribution().get("c"), 0);
            assertEquals(1.5, stats.getEntropy(), 0);
        }, fieldType);
    }

    public void testFieldScriptSingleValuedField() throws IOException {
        final TextFieldMapper.TextFieldType fieldType = new TextFieldMapper.TextFieldType("text");
        fieldType.setFielddata(true);

        final StringStatsAggregationBuilder aggregationBuilder = new StringStatsAggregationBuilder("_name")
            .script(new Script(ScriptType.INLINE, MockScriptEngine.NAME, FIELD_SCRIPT_NAME,
                singletonMap("field", fieldType.name())));

        testAggregation(aggregationBuilder, new MatchAllDocsQuery(), iw -> {
            iw.addDocument(singleton(new TextField(fieldType.name(), "b", Field.Store.NO)));
            iw.addDocument(singleton(new TextField(fieldType.name(), "b", Field.Store.NO)));
        }, stats -> {
            assertEquals(2, stats.getCount());
            assertEquals(2, stats.getMaxLength());
            assertEquals(2, stats.getMinLength());
            assertEquals(2.0, stats.getAvgLength(), 0);
            assertEquals(2, stats.getDistribution().size());
            assertEquals(0.5, stats.getDistribution().get("a"), 0);
            assertEquals(0.5, stats.getDistribution().get("b"), 0);
            assertEquals(1.0, stats.getEntropy(), 0);
        }, fieldType);
    }

    public void testFieldScriptMultiValuedField() throws IOException {
        final TextFieldMapper.TextFieldType fieldType = new TextFieldMapper.TextFieldType("text");
        fieldType.setFielddata(true);

        final StringStatsAggregationBuilder aggregationBuilder = new StringStatsAggregationBuilder("_name")
            .script(new Script(ScriptType.INLINE, MockScriptEngine.NAME, FIELD_SCRIPT_NAME,
                singletonMap("field", fieldType.name())));

        testAggregation(aggregationBuilder, new MatchAllDocsQuery(), iw -> {
            Set<TextField> doc = new java.util.HashSet<>();
            doc.add(new TextField(fieldType.name(), "b", Field.Store.NO));
            doc.add(new TextField(fieldType.name(), "c", Field.Store.NO));
            iw.addDocument(doc);
            Set<TextField> doc1 = new java.util.HashSet<>();
            doc1.add(new TextField(fieldType.name(), "b", Field.Store.NO));
            doc1.add(new TextField(fieldType.name(), "c", Field.Store.NO));
            iw.addDocument(doc1);
        }, stats -> {
            assertEquals(4, stats.getCount());
            assertEquals(2, stats.getMaxLength());
            assertEquals(2, stats.getMinLength());
            assertEquals(2.0, stats.getAvgLength(), 0);
            assertEquals(3, stats.getDistribution().size());
            assertEquals(0.5, stats.getDistribution().get("a"), 0);
            assertEquals(0.25, stats.getDistribution().get("b"), 0);
            assertEquals(0.25, stats.getDistribution().get("c"), 0);
            assertEquals(1.5, stats.getEntropy(), 0);
        }, fieldType);
    }

    private void testAggregation(Query query,
        CheckedConsumer<RandomIndexWriter, IOException> buildIndex,
        Consumer<InternalStringStats> verify) throws IOException {
        TextFieldMapper.TextFieldType fieldType = new TextFieldMapper.TextFieldType("text");
        fieldType.setFielddata(true);

        AggregationBuilder aggregationBuilder = new StringStatsAggregationBuilder("_name").field("text");
        testAggregation(aggregationBuilder, query, buildIndex, verify, fieldType);
    }

    private void testAggregation(
        AggregationBuilder aggregationBuilder,
        Query query,
        CheckedConsumer<RandomIndexWriter, IOException> buildIndex,
        Consumer<InternalStringStats> verify,
        MappedFieldType... fieldTypes
    ) throws IOException {
        testCase(aggregationBuilder, query, buildIndex, verify, fieldTypes);
    }

    @Override
    protected AggregationBuilder createAggBuilderForTypeTest(MappedFieldType fieldType, String fieldName) {
        return new StringStatsAggregationBuilder("_name")
            .field(fieldName);
    }

    @Override
    protected List<ValuesSourceType> getSupportedValuesSourceTypes() {
        return singletonList(CoreValuesSourceType.KEYWORD);
    }

    @Override
    protected List<String> unsupportedMappedFieldTypes() {
        return singletonList(IpFieldMapper.CONTENT_TYPE);
    }

    @Override
    protected ScriptService getMockScriptService() {
        final Map<String, Function<Map<String, Object>, Object>> scripts = new HashMap<>();
        scripts.put(VALUE_SCRIPT_NAME, vars -> "a" + vars.get("_value"));
        scripts.put(FIELD_SCRIPT_NAME, vars -> {
            final String fieldName = (String) vars.get("field");
            final LeafDocLookup lookup = (LeafDocLookup) vars.get("doc");
            return lookup.get(fieldName).stream()
                .map(value -> "a" + value)
                .collect(Collectors.toList());
        });
        final MockScriptEngine engine = new MockScriptEngine(MockScriptEngine.NAME, scripts, emptyMap());
        final Map<String, ScriptEngine> engines = singletonMap(engine.getType(), engine);
        return new ScriptService(Settings.EMPTY, engines, ScriptModule.CORE_CONTEXTS);
    }
}
