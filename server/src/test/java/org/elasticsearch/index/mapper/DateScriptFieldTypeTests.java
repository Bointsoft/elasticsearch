/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0 and the Server Side Public License, v 1; you may not use this file except
 * in compliance with, at your election, the Elastic License 2.0 or the Server
 * Side Public License, v 1.
 */

package org.elasticsearch.index.mapper;

import org.apache.lucene.document.StoredField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.RandomIndexWriter;
import org.apache.lucene.index.SortedNumericDocValues;
import org.apache.lucene.search.Collector;
import org.apache.lucene.search.Explanation;
import org.apache.lucene.search.FieldDoc;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.LeafCollector;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.Scorable;
import org.apache.lucene.search.ScoreMode;
import org.apache.lucene.search.Sort;
import org.apache.lucene.search.SortField;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.TopFieldDocs;
import org.apache.lucene.store.Directory;
import org.apache.lucene.util.BytesRef;
import org.elasticsearch.ElasticsearchParseException;
import org.elasticsearch.Version;
import org.elasticsearch.common.CheckedSupplier;
import org.elasticsearch.common.Strings;
import org.elasticsearch.common.geo.ShapeRelation;
import org.elasticsearch.common.lucene.search.function.ScriptScoreQuery;
import org.elasticsearch.common.time.DateFormatter;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.index.fielddata.ScriptDocValues;
import org.elasticsearch.index.query.SearchExecutionContext;
import org.elasticsearch.index.fielddata.DateScriptFieldData;
import org.elasticsearch.script.DateFieldScript;
import org.elasticsearch.script.DocReader;
import org.elasticsearch.script.ScoreScript;
import org.elasticsearch.script.Script;
import org.elasticsearch.script.ScriptType;
import org.elasticsearch.search.MultiValueMode;

import java.io.IOException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static java.util.Collections.emptyMap;
import static org.hamcrest.Matchers.arrayWithSize;
import static org.hamcrest.Matchers.closeTo;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.instanceOf;

public class DateScriptFieldTypeTests extends AbstractNonTextScriptFieldTypeTestCase {

    public void testFromSource() throws IOException {
        MapperService mapperService = createMapperService(runtimeFieldMapping(b -> b.field("type", "date")));
        ParsedDocument doc = mapperService.documentMapper().parse(source(b -> b.field("field", 1545)));
        withLuceneIndex(mapperService, iw -> iw.addDocuments(doc.docs()), ir -> {
            MappedFieldType ft = mapperService.fieldType("field");
            SearchExecutionContext sec = createSearchExecutionContext(mapperService);
            Query rangeQuery = ft.rangeQuery("1200-01-01", "2020-01-01", false, false, ShapeRelation.CONTAINS, null, null, sec);
            IndexSearcher searcher = new IndexSearcher(ir);
            assertEquals(1, searcher.count(rangeQuery));
        });
    }

    public void testDateWithFormat() throws IOException {
        CheckedSupplier<XContentBuilder, IOException> mapping = () -> runtimeFieldMapping(b -> {
            minimalMapping(b);
            b.field("format", "yyyy-MM-dd");
        });
        MapperService mapperService = createMapperService(mapping.get());
        MappedFieldType fieldType = mapperService.fieldType("field");
        assertThat(fieldType, instanceOf(DateScriptFieldType.class));
        assertEquals(Strings.toString(mapping.get()), Strings.toString(mapperService.documentMapper().mapping()));
    }

    public void testDateWithLocale() throws IOException {
        CheckedSupplier<XContentBuilder, IOException> mapping = () -> runtimeFieldMapping(b -> {
            minimalMapping(b);
            b.field("locale", "en_GB");
        });
        MapperService mapperService = createMapperService(mapping.get());
        MappedFieldType fieldType = mapperService.fieldType("field");
        assertThat(fieldType, instanceOf(DateScriptFieldType.class));
        assertEquals(Strings.toString(mapping.get()), Strings.toString(mapperService.documentMapper().mapping()));
    }

    public void testDateWithLocaleAndFormat() throws IOException {
        CheckedSupplier<XContentBuilder, IOException> mapping = () -> runtimeFieldMapping(b -> {
            minimalMapping(b);
            b.field("format", "yyyy-MM-dd").field("locale", "en_GB");
        });
        MapperService mapperService = createMapperService(mapping.get());
        MappedFieldType fieldType = mapperService.fieldType("field");
        assertThat(fieldType, instanceOf(DateScriptFieldType.class));
        assertEquals(Strings.toString(mapping.get()), Strings.toString(mapperService.documentMapper().mapping()));
    }

    public void testFormat() throws IOException {
        assertThat(simpleMappedFieldType().docValueFormat("date", null).format(1595432181354L), equalTo("2020-07-22"));
        assertThat(
            simpleMappedFieldType().docValueFormat("strict_date_optional_time", null).format(1595432181354L),
            equalTo("2020-07-22T15:36:21.354Z")
        );
        assertThat(
            simpleMappedFieldType().docValueFormat("strict_date_optional_time", ZoneId.of("America/New_York")).format(1595432181354L),
            equalTo("2020-07-22T11:36:21.354-04:00")
        );
        assertThat(
            simpleMappedFieldType().docValueFormat(null, ZoneId.of("America/New_York")).format(1595432181354L),
            equalTo("2020-07-22T11:36:21.354-04:00")
        );
        assertThat(coolFormattedFieldType().docValueFormat(null, null).format(1595432181354L), equalTo("2020-07-22(-■_■)15:36:21.354Z"));
    }

    public void testFormatDuel() throws IOException {
        DateFormatter formatter = DateFormatter.forPattern(randomDateFormatterPattern()).withLocale(randomLocale(random()));
        DateScriptFieldType scripted = build(
            new Script(ScriptType.INLINE, "test", "read_timestamp", org.elasticsearch.core.Map.of()),
            formatter
        );
        DateFieldMapper.DateFieldType indexed = new DateFieldMapper.DateFieldType("test", formatter);
        for (int i = 0; i < 100; i++) {
            long date = randomDate();
            assertThat(indexed.docValueFormat(null, null).format(date), equalTo(scripted.docValueFormat(null, null).format(date)));
            String format = randomDateFormatterPattern();
            assertThat(indexed.docValueFormat(format, null).format(date), equalTo(scripted.docValueFormat(format, null).format(date)));
            ZoneId zone = randomZone();
            assertThat(indexed.docValueFormat(null, zone).format(date), equalTo(scripted.docValueFormat(null, zone).format(date)));
            assertThat(indexed.docValueFormat(format, zone).format(date), equalTo(scripted.docValueFormat(format, zone).format(date)));
        }
    }

    @Override
    public void testDocValues() throws IOException {
        try (Directory directory = newDirectory(); RandomIndexWriter iw = new RandomIndexWriter(random(), directory)) {
            iw.addDocument(
                org.elasticsearch.core.List.of(new StoredField("_source", new BytesRef("{\"timestamp\": [1595432181354]}")))
            );
            iw.addDocument(
                org.elasticsearch.core.List.of(
                    new StoredField("_source", new BytesRef("{\"timestamp\": [1595432181356, 1595432181351]}"))
                )
            );
            List<Long> results = new ArrayList<>();
            try (DirectoryReader reader = iw.getReader()) {
                IndexSearcher searcher = newSearcher(reader);
                DateScriptFieldType ft = build("add_days", org.elasticsearch.core.Map.of("days", 1));
                DateScriptFieldData ifd = ft.fielddataBuilder("test", mockContext()::lookup).build(null, null);
                searcher.search(new MatchAllDocsQuery(), new Collector() {
                    @Override
                    public ScoreMode scoreMode() {
                        return ScoreMode.COMPLETE_NO_SCORES;
                    }

                    @Override
                    public LeafCollector getLeafCollector(LeafReaderContext context) throws IOException {
                        SortedNumericDocValues dv = ifd.load(context).getLongValues();
                        return new LeafCollector() {
                            @Override
                            public void setScorer(Scorable scorer) throws IOException {}

                            @Override
                            public void collect(int doc) throws IOException {
                                if (dv.advanceExact(doc)) {
                                    for (int i = 0; i < dv.docValueCount(); i++) {
                                        results.add(dv.nextValue());
                                    }
                                }
                            }
                        };
                    }
                });
                assertThat(results, equalTo(org.elasticsearch.core.List.of(1595518581354L, 1595518581351L, 1595518581356L)));
            }
        }
    }

    @Override
    public void testSort() throws IOException {
        try (Directory directory = newDirectory(); RandomIndexWriter iw = new RandomIndexWriter(random(), directory)) {
            iw.addDocument(
                org.elasticsearch.core.List.of(new StoredField("_source", new BytesRef("{\"timestamp\": [1595432181354]}")))
            );
            iw.addDocument(
                org.elasticsearch.core.List.of(new StoredField("_source", new BytesRef("{\"timestamp\": [1595432181351]}")))
            );
            iw.addDocument(
                org.elasticsearch.core.List.of(new StoredField("_source", new BytesRef("{\"timestamp\": [1595432181356]}")))
            );
            try (DirectoryReader reader = iw.getReader()) {
                IndexSearcher searcher = newSearcher(reader);
                DateScriptFieldData ifd = simpleMappedFieldType().fielddataBuilder("test", mockContext()::lookup).build(null, null);
                SortField sf = ifd.sortField(null, MultiValueMode.MIN, null, false);
                TopFieldDocs docs = searcher.search(new MatchAllDocsQuery(), 3, new Sort(sf));
                assertThat(readSource(reader, docs.scoreDocs[0].doc), equalTo("{\"timestamp\": [1595432181351]}"));
                assertThat(readSource(reader, docs.scoreDocs[1].doc), equalTo("{\"timestamp\": [1595432181354]}"));
                assertThat(readSource(reader, docs.scoreDocs[2].doc), equalTo("{\"timestamp\": [1595432181356]}"));
                assertThat((Long) (((FieldDoc) docs.scoreDocs[0]).fields[0]), equalTo(1595432181351L));
                assertThat((Long) (((FieldDoc) docs.scoreDocs[1]).fields[0]), equalTo(1595432181354L));
                assertThat((Long) (((FieldDoc) docs.scoreDocs[2]).fields[0]), equalTo(1595432181356L));
            }
        }
    }

    @Override
    public void testUsedInScript() throws IOException {
        try (Directory directory = newDirectory(); RandomIndexWriter iw = new RandomIndexWriter(random(), directory)) {
            iw.addDocument(
                org.elasticsearch.core.List.of(new StoredField("_source", new BytesRef("{\"timestamp\": [1595432181354]}")))
            );
            iw.addDocument(
                org.elasticsearch.core.List.of(new StoredField("_source", new BytesRef("{\"timestamp\": [1595432181351]}")))
            );
            iw.addDocument(
                org.elasticsearch.core.List.of(new StoredField("_source", new BytesRef("{\"timestamp\": [1595432181356]}")))
            );
            try (DirectoryReader reader = iw.getReader()) {
                IndexSearcher searcher = newSearcher(reader);
                SearchExecutionContext searchContext = mockContext(true, simpleMappedFieldType());
                assertThat(searcher.count(new ScriptScoreQuery(new MatchAllDocsQuery(), new Script("test"), new ScoreScript.LeafFactory() {
                    @Override
                    public boolean needs_score() {
                        return false;
                    }

                    @Override
                    public ScoreScript newInstance(DocReader docReader) throws IOException {
                        return new ScoreScript(org.elasticsearch.core.Map.of(), searchContext.lookup(), docReader) {
                            @Override
                            public double execute(ExplanationHolder explanation) {
                                ScriptDocValues.Dates dates = (ScriptDocValues.Dates) getDoc().get("test");
                                return dates.get(0).toInstant().toEpochMilli() % 1000;
                            }
                        };
                    }
                }, searchContext.lookup(), 354.5f, "test", 0, Version.CURRENT)), equalTo(1));
            }
        }
    }

    public void testDistanceFeatureQuery() throws IOException {
        try (Directory directory = newDirectory(); RandomIndexWriter iw = new RandomIndexWriter(random(), directory)) {
            iw.addDocuments(
                org.elasticsearch.core.List.of(
                    org.elasticsearch.core.List.of(new StoredField("_source", new BytesRef("{\"timestamp\": [1595432181354]}"))),
                    org.elasticsearch.core.List.of(new StoredField("_source", new BytesRef("{\"timestamp\": [1595432181351]}"))),
                    org.elasticsearch.core.List.of(
                        new StoredField("_source", new BytesRef("{\"timestamp\": [1595432181356, 1]}"))
                    ),
                    org.elasticsearch.core.List.of(new StoredField("_source", new BytesRef("{\"timestamp\": []}")))
                )
            );
            try (DirectoryReader reader = iw.getReader()) {
                IndexSearcher searcher = newSearcher(reader);
                Query query = simpleMappedFieldType().distanceFeatureQuery(1595432181354L, "1ms", mockContext());
                TopDocs docs = searcher.search(query, 4);
                assertThat(docs.scoreDocs, arrayWithSize(3));
                assertThat(readSource(reader, docs.scoreDocs[0].doc), equalTo("{\"timestamp\": [1595432181354]}"));
                assertThat(docs.scoreDocs[0].score, equalTo(1.0F));
                assertThat(readSource(reader, docs.scoreDocs[1].doc), equalTo("{\"timestamp\": [1595432181356, 1]}"));
                assertThat((double) docs.scoreDocs[1].score, closeTo(.333, .001));
                assertThat(readSource(reader, docs.scoreDocs[2].doc), equalTo("{\"timestamp\": [1595432181351]}"));
                assertThat((double) docs.scoreDocs[2].score, closeTo(.250, .001));
                Explanation explanation = query.createWeight(searcher, ScoreMode.TOP_SCORES, 1.0F)
                    .explain(reader.leaves().get(0), docs.scoreDocs[0].doc);
                assertThat(explanation.toString(), containsString("1.0 = Distance score, computed as weight * pivot / (pivot"));
                assertThat(explanation.toString(), containsString("1.0 = weight"));
                assertThat(explanation.toString(), containsString("1 = pivot"));
                assertThat(explanation.toString(), containsString("1595432181354 = origin"));
                assertThat(explanation.toString(), containsString("1595432181354 = current value"));
            }
        }
    }

    public void testDistanceFeatureQueryIsExpensive() throws IOException {
        checkExpensiveQuery(this::randomDistanceFeatureQuery);
    }

    public void testDistanceFeatureQueryInLoop() throws IOException {
        checkLoop(this::randomDistanceFeatureQuery);
    }

    private Query randomDistanceFeatureQuery(MappedFieldType ft, SearchExecutionContext ctx) {
        return ft.distanceFeatureQuery(randomDate(), randomTimeValue(), ctx);
    }

    @Override
    public void testExistsQuery() throws IOException {
        try (Directory directory = newDirectory(); RandomIndexWriter iw = new RandomIndexWriter(random(), directory)) {
            iw.addDocument(
                org.elasticsearch.core.List.of(new StoredField("_source", new BytesRef("{\"timestamp\": [1595432181356]}")))
            );
            iw.addDocument(org.elasticsearch.core.List.of(new StoredField("_source", new BytesRef("{\"timestamp\": []}"))));
            try (DirectoryReader reader = iw.getReader()) {
                IndexSearcher searcher = newSearcher(reader);
                assertThat(searcher.count(simpleMappedFieldType().existsQuery(mockContext())), equalTo(1));
            }
        }
    }

    @Override
    public void testRangeQuery() throws IOException {
        try (Directory directory = newDirectory(); RandomIndexWriter iw = new RandomIndexWriter(random(), directory)) {
            iw.addDocument(
                org.elasticsearch.core.List.of(new StoredField("_source", new BytesRef("{\"timestamp\": [1595432181354]}")))
            );
            iw.addDocument(
                org.elasticsearch.core.List.of(new StoredField("_source", new BytesRef("{\"timestamp\": [1595432181351]}")))
            );
            iw.addDocument(
                org.elasticsearch.core.List.of(new StoredField("_source", new BytesRef("{\"timestamp\": [1595432181356]}")))
            );
            try (DirectoryReader reader = iw.getReader()) {
                IndexSearcher searcher = newSearcher(reader);
                MappedFieldType ft = simpleMappedFieldType();
                assertThat(
                    searcher.count(
                        ft.rangeQuery("2020-07-22T15:36:21.356Z", "2020-07-23T00:00:00.000Z", true, true, null, null, null, mockContext())
                    ),
                    equalTo(1)
                );
                assertThat(
                    searcher.count(
                        ft.rangeQuery("2020-07-22T00:00:00.00Z", "2020-07-22T15:36:21.354Z", true, true, null, null, null, mockContext())
                    ),
                    equalTo(2)
                );
                assertThat(
                    searcher.count(ft.rangeQuery(1595432181351L, 1595432181356L, true, true, null, null, null, mockContext())),
                    equalTo(3)
                );
                assertThat(
                    searcher.count(
                        ft.rangeQuery("2020-07-22T15:36:21.356Z", "2020-07-23T00:00:00.000Z", true, false, null, null, null, mockContext())
                    ),
                    equalTo(1)
                );
                assertThat(
                    searcher.count(
                        ft.rangeQuery("2020-07-22T15:36:21.356Z", "2020-07-23T00:00:00.000Z", false, false, null, null, null, mockContext())
                    ),
                    equalTo(0)
                );
                checkBadDate(
                    () -> searcher.count(
                        ft.rangeQuery(
                            "2020-07-22(-■_■)00:00:00.000Z",
                            "2020-07-23(-■_■)00:00:00.000Z",
                            false,
                            false,
                            null,
                            null,
                            null,
                            mockContext()
                        )
                    )
                );
                assertThat(
                    searcher.count(
                        coolFormattedFieldType().rangeQuery(
                            "2020-07-22(-■_■)00:00:00.000Z",
                            "2020-07-23(-■_■)00:00:00.000Z",
                            false,
                            false,
                            null,
                            null,
                            null,
                            mockContext()
                        )
                    ),
                    equalTo(3)
                );
            }
        }
    }

    @Override
    protected Query randomRangeQuery(MappedFieldType ft, SearchExecutionContext ctx) {
        long d1 = randomDate();
        long d2 = randomValueOtherThan(d1, DateScriptFieldTypeTests::randomDate);
        if (d1 > d2) {
            long backup = d2;
            d2 = d1;
            d1 = backup;
        }
        return ft.rangeQuery(d1, d2, randomBoolean(), randomBoolean(), null, null, null, ctx);
    }

    @Override
    public void testTermQuery() throws IOException {
        try (Directory directory = newDirectory(); RandomIndexWriter iw = new RandomIndexWriter(random(), directory)) {
            iw.addDocument(
                org.elasticsearch.core.List.of(new StoredField("_source", new BytesRef("{\"timestamp\": [1595432181354]}")))
            );
            iw.addDocument(
                org.elasticsearch.core.List.of(new StoredField("_source", new BytesRef("{\"timestamp\": [1595432181355]}")))
            );
            try (DirectoryReader reader = iw.getReader()) {
                IndexSearcher searcher = newSearcher(reader);
                assertThat(searcher.count(simpleMappedFieldType().termQuery("2020-07-22T15:36:21.354Z", mockContext())), equalTo(1));
                assertThat(searcher.count(simpleMappedFieldType().termQuery("1595432181355", mockContext())), equalTo(1));
                assertThat(searcher.count(simpleMappedFieldType().termQuery(1595432181354L, mockContext())), equalTo(1));
                assertThat(searcher.count(simpleMappedFieldType().termQuery(2595432181354L, mockContext())), equalTo(0));
                assertThat(
                    searcher.count(
                        build("add_days", org.elasticsearch.core.Map.of("days", 1)).termQuery(
                            "2020-07-23T15:36:21.354Z",
                            mockContext()
                        )
                    ),
                    equalTo(1)
                );
                checkBadDate(() -> searcher.count(simpleMappedFieldType().termQuery("2020-07-22(-■_■)15:36:21.354Z", mockContext())));
                assertThat(searcher.count(coolFormattedFieldType().termQuery("2020-07-22(-■_■)15:36:21.354Z", mockContext())), equalTo(1));
            }
        }
    }

    @Override
    protected Query randomTermQuery(MappedFieldType ft, SearchExecutionContext ctx) {
        return ft.termQuery(randomDate(), ctx);
    }

    @Override
    public void testTermsQuery() throws IOException {
        try (Directory directory = newDirectory(); RandomIndexWriter iw = new RandomIndexWriter(random(), directory)) {
            iw.addDocument(
                org.elasticsearch.core.List.of(new StoredField("_source", new BytesRef("{\"timestamp\": [1595432181354]}")))
            );
            iw.addDocument(
                org.elasticsearch.core.List.of(new StoredField("_source", new BytesRef("{\"timestamp\": [1595432181355]}")))
            );
            try (DirectoryReader reader = iw.getReader()) {
                MappedFieldType ft = simpleMappedFieldType();
                IndexSearcher searcher = newSearcher(reader);
                assertThat(
                    searcher.count(ft.termsQuery(org.elasticsearch.core.List.of("2020-07-22T15:36:21.354Z"), mockContext())),
                    equalTo(1)
                );
                assertThat(
                    searcher.count(ft.termsQuery(org.elasticsearch.core.List.of("1595432181354"), mockContext())),
                    equalTo(1)
                );
                assertThat(
                    searcher.count(ft.termsQuery(org.elasticsearch.core.List.of(1595432181354L), mockContext())),
                    equalTo(1)
                );
                assertThat(
                    searcher.count(ft.termsQuery(org.elasticsearch.core.List.of(2595432181354L), mockContext())),
                    equalTo(0)
                );
                assertThat(
                    searcher.count(ft.termsQuery(org.elasticsearch.core.List.of(1595432181354L, 2595432181354L), mockContext())),
                    equalTo(1)
                );
                assertThat(
                    searcher.count(ft.termsQuery(org.elasticsearch.core.List.of(2595432181354L, 1595432181354L), mockContext())),
                    equalTo(1)
                );
                assertThat(
                    searcher.count(ft.termsQuery(org.elasticsearch.core.List.of(1595432181355L, 1595432181354L), mockContext())),
                    equalTo(2)
                );
                checkBadDate(
                    () -> searcher.count(
                        simpleMappedFieldType().termsQuery(
                            org.elasticsearch.core.List.of("2020-07-22T15:36:21.354Z", "2020-07-22(-■_■)15:36:21.354Z"),
                            mockContext()
                        )
                    )
                );
                assertThat(
                    searcher.count(
                        coolFormattedFieldType().termsQuery(
                            org.elasticsearch.core.List.of("2020-07-22(-■_■)15:36:21.354Z", "2020-07-22(-■_■)15:36:21.355Z"),
                            mockContext()
                        )
                    ),
                    equalTo(2)
                );
            }
        }
    }

    @Override
    protected Query randomTermsQuery(MappedFieldType ft, SearchExecutionContext ctx) {
        return ft.termsQuery(randomList(1, 100, DateScriptFieldTypeTests::randomDate), ctx);
    }

    @Override
    protected DateScriptFieldType simpleMappedFieldType() {
        return build("read_timestamp");
    }

    @Override
    protected MappedFieldType loopFieldType() {
        return build("loop");
    }

    private DateScriptFieldType coolFormattedFieldType() {
        return build(simpleMappedFieldType().script, DateFormatter.forPattern("yyyy-MM-dd(-■_■)HH:mm:ss.SSSz||epoch_millis"));
    }

    @Override
    protected String typeName() {
        return "date";
    }

    private static DateScriptFieldType build(String code) {
        return build(code, Collections.emptyMap());
    }

    private static DateScriptFieldType build(String code, Map<String, Object> params) {
        return build(new Script(ScriptType.INLINE, "test", code, params), DateFieldMapper.DEFAULT_DATE_TIME_FORMATTER);
    }

    private static DateFieldScript.Factory factory(Script script) {
        switch (script.getIdOrCode()) {
            case "read_timestamp":
                return (fieldName, params, lookup, formatter) -> ctx -> new DateFieldScript(fieldName, params, lookup, formatter, ctx) {
                    @Override
                    public void execute() {
                        for (Object timestamp : (List<?>) lookup.source().get("timestamp")) {
                            DateFieldScript.Parse parse = new DateFieldScript.Parse(this);
                            emit(parse.parse(timestamp));
                        }
                    }
                };
            case "add_days":
                return (fieldName, params, lookup, formatter) -> ctx -> new DateFieldScript(fieldName, params, lookup, formatter, ctx) {
                    @Override
                    public void execute() {
                        for (Object timestamp : (List<?>) lookup.source().get("timestamp")) {
                            long epoch = (Long) timestamp;
                            ZonedDateTime dt = ZonedDateTime.ofInstant(Instant.ofEpochMilli(epoch), ZoneId.of("UTC"));
                            dt = dt.plus(((Number) params.get("days")).longValue(), ChronoUnit.DAYS);
                            emit(dt.toInstant().toEpochMilli());
                        }
                    }
                };
            case "loop":
                return (fieldName, params, lookup, formatter) -> {
                    // Indicate that this script wants the field call "test", which *is* the name of this field
                    lookup.forkAndTrackFieldReferences("test");
                    throw new IllegalStateException("shoud have thrown on the line above");
                };
            default:
                throw new IllegalArgumentException("unsupported script [" + script.getIdOrCode() + "]");
        }
    }

    private static DateScriptFieldType build(Script script, DateFormatter dateTimeFormatter) {
        return new DateScriptFieldType("test", factory(script), dateTimeFormatter, script, emptyMap());
    }

    private static long randomDate() {
        return Math.abs(randomLong() % (2 * (long) 10e11)); // 1970-01-01T00:00:00Z - 2033-05-18T05:33:20.000+02:00
    }

    private void checkBadDate(ThrowingRunnable queryBuilder) {
        Exception e = expectThrows(ElasticsearchParseException.class, queryBuilder);
        assertThat(e.getMessage(), containsString("failed to parse date field"));
    }
}
