/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0 and the Server Side Public License, v 1; you may not use this file except
 * in compliance with, at your election, the Elastic License 2.0 or the Server
 * Side Public License, v 1.
 */

package org.elasticsearch.search.geo;

import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.action.search.SearchAction;
import org.elasticsearch.action.search.SearchPhaseExecutionException;
import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.common.geo.ShapeRelation;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentFactory;
import org.elasticsearch.common.xcontent.XContentType;
import org.elasticsearch.geometry.Circle;
import org.elasticsearch.geometry.Geometry;
import org.elasticsearch.geometry.Line;
import org.elasticsearch.geometry.LinearRing;
import org.elasticsearch.geometry.MultiLine;
import org.elasticsearch.geometry.MultiPoint;
import org.elasticsearch.geometry.MultiPolygon;
import org.elasticsearch.geometry.Point;
import org.elasticsearch.geometry.Polygon;
import org.elasticsearch.geometry.Rectangle;
import org.elasticsearch.geometry.ShapeType;
import org.elasticsearch.index.query.GeoShapeQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.plugins.Plugin;
import org.elasticsearch.search.SearchHits;
import org.elasticsearch.test.ESSingleNodeTestCase;
import org.elasticsearch.test.TestGeoShapeFieldMapperPlugin;

import java.util.Collection;
import java.util.Collections;

import static org.elasticsearch.action.support.WriteRequest.RefreshPolicy.IMMEDIATE;
import static org.elasticsearch.common.xcontent.XContentFactory.jsonBuilder;
import static org.elasticsearch.test.hamcrest.ElasticsearchAssertions.assertHitCount;
import static org.elasticsearch.test.hamcrest.ElasticsearchAssertions.assertSearchResponse;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;

public abstract class GeoPointShapeQueryTestCase extends ESSingleNodeTestCase {
    @Override
    protected Collection<Class<? extends Plugin>> getPlugins() {
        return Collections.singleton(TestGeoShapeFieldMapperPlugin.class);
    }

    protected abstract void createMapping(String indexName, String type, String fieldName, Settings settings) throws Exception;

    protected void createMapping(String indexName, String type, String fieldName) throws Exception {
        createMapping(indexName, type, fieldName, Settings.EMPTY);
    }

    protected static final String defaultGeoFieldName = "geo";
    protected static final String defaultIndexName = "test";
    protected static final String defaultType = "_doc";

    public void testNullShape() throws Exception {
        createMapping(defaultIndexName, defaultType, defaultGeoFieldName);
        ensureGreen();

        client().prepareIndex(defaultIndexName, defaultType)
            .setId("aNullshape")
            .setSource("{\"geo\": null}", XContentType.JSON)
            .setRefreshPolicy(IMMEDIATE).get();
        GetResponse result = client().prepareGet(defaultIndexName,"_doc", "aNullshape").get();
        assertThat(result.getField("location"), nullValue());
    };

    public void testIndexPointsFilterRectangle() throws Exception {
        createMapping(defaultIndexName, defaultType, defaultGeoFieldName);
        ensureGreen();

        client().prepareIndex(defaultIndexName,"_doc").setId("1").setSource(jsonBuilder()
            .startObject()
            .field("name", "Document 1")
            .field(defaultGeoFieldName, "POINT(-30 -30)")
            .endObject()).setRefreshPolicy(IMMEDIATE).get();

        client().prepareIndex(defaultIndexName,"_doc").setId("2").setSource(jsonBuilder()
            .startObject()
            .field("name", "Document 2")
            .field(defaultGeoFieldName, "POINT(-45 -50)")
            .endObject()).setRefreshPolicy(IMMEDIATE).get();

        Geometry geometry = new Rectangle(-45, 45, 45, -45);
        SearchResponse searchResponse = client().prepareSearch(defaultIndexName)
            .setQuery(QueryBuilders.geoShapeQuery(defaultGeoFieldName, geometry)
                .relation(ShapeRelation.INTERSECTS))
            .get();

        assertSearchResponse(searchResponse);
        assertThat(searchResponse.getHits().getTotalHits().value, equalTo(1L));
        assertThat(searchResponse.getHits().getHits().length, equalTo(1));
        assertThat(searchResponse.getHits().getAt(0).getId(), equalTo("1"));

        // default query, without specifying relation (expect intersects)
        searchResponse = client().prepareSearch(defaultIndexName)
            .setQuery(QueryBuilders.geoShapeQuery(defaultGeoFieldName, geometry))
            .get();

        assertSearchResponse(searchResponse);
        assertThat(searchResponse.getHits().getTotalHits().value, equalTo(1L));
        assertThat(searchResponse.getHits().getHits().length, equalTo(1));
        assertThat(searchResponse.getHits().getAt(0).getId(), equalTo("1"));
    }

    public void testIndexPointsCircle() throws Exception {
        createMapping(defaultIndexName, defaultType, defaultGeoFieldName);
        ensureGreen();

        client().prepareIndex(defaultIndexName,"_doc").setId("1").setSource(jsonBuilder()
            .startObject()
            .field("name", "Document 1")
            .field(defaultGeoFieldName, "POINT(-30 -30)")
            .endObject()).setRefreshPolicy(IMMEDIATE).get();

        client().prepareIndex(defaultIndexName,"_doc").setId("2").setSource(jsonBuilder()
            .startObject()
            .field("name", "Document 2")
            .field(defaultGeoFieldName, "POINT(-45 -50)")
            .endObject()).setRefreshPolicy(IMMEDIATE).get();

        Geometry geometry = new Circle(-30, -30, 100);

        try {
            client().prepareSearch(defaultIndexName)
                .setQuery(QueryBuilders.geoShapeQuery(defaultGeoFieldName, geometry)
                    .relation(ShapeRelation.INTERSECTS))
                .get();
        } catch (
            Exception e) {
            assertThat(e.getCause().getMessage(),
                containsString("failed to create query: "
                    + ShapeType.CIRCLE + " geometry is not supported"));
        }
    }

    public void testIndexPointsPolygon() throws Exception {
        createMapping(defaultIndexName, defaultType, defaultGeoFieldName);
        ensureGreen();

        client().prepareIndex(defaultIndexName,"_doc").setId("1").setSource(jsonBuilder()
            .startObject()
            .field(defaultGeoFieldName, "POINT(-30 -30)")
            .endObject()).setRefreshPolicy(IMMEDIATE).get();

        client().prepareIndex(defaultIndexName,"_doc").setId("2").setSource(jsonBuilder()
            .startObject()
            .field(defaultGeoFieldName, "POINT(-45 -50)")
            .endObject()).setRefreshPolicy(IMMEDIATE).get();

        Polygon polygon = new Polygon(
            new LinearRing(
                new double[] {-35, -35, -25, -25, -35},
                new double[] {-35, -25, -25, -35, -35}
            )
        );

        SearchResponse searchResponse = client().prepareSearch(defaultIndexName)
            .setQuery(QueryBuilders.geoShapeQuery(defaultGeoFieldName, polygon)
                .relation(ShapeRelation.INTERSECTS))
            .get();

        assertSearchResponse(searchResponse);
        assertHitCount(searchResponse, 1);
        SearchHits searchHits = searchResponse.getHits();
        assertThat(searchHits.getAt(0).getId(), equalTo("1"));
    }

    public void testIndexPointsMultiPolygon() throws Exception {
        createMapping(defaultIndexName, defaultType, defaultGeoFieldName);
        ensureGreen();

        client().prepareIndex(defaultIndexName,"_doc").setId("1").setSource(jsonBuilder()
            .startObject()
            .field("name", "Document 1")
            .field(defaultGeoFieldName, "POINT(-30 -30)")
            .endObject()).setRefreshPolicy(IMMEDIATE).get();

        client().prepareIndex(defaultIndexName,"_doc").setId("2").setSource(jsonBuilder()
            .startObject()
            .field("name", "Document 2")
            .field(defaultGeoFieldName, "POINT(-40 -40)")
            .endObject()).setRefreshPolicy(IMMEDIATE).get();

        client().prepareIndex(defaultIndexName,"_doc").setId("3").setSource(jsonBuilder()
            .startObject()
            .field("name", "Document 3")
            .field(defaultGeoFieldName, "POINT(-50 -50)")
            .endObject()).setRefreshPolicy(IMMEDIATE).get();

        Polygon encloseDocument1Cb = new Polygon(
            new LinearRing(
                new double[] {-35, -35, -25, -25, -35},
                new double[] {-35, -25, -25, -35, -35}
            )
        );

        Polygon encloseDocument2Cb = new Polygon(
            new LinearRing(
                new double[] {-55, -55, -45, -45, -55},
                new double[] {-55, -45, -45, -55, -55}
            )
        );

        MultiPolygon multiPolygon = new MultiPolygon(org.elasticsearch.core.List.of(encloseDocument1Cb, encloseDocument2Cb));
        {
            SearchResponse searchResponse = client().prepareSearch(defaultIndexName)
                .setQuery(QueryBuilders.geoShapeQuery(defaultGeoFieldName, multiPolygon)
                    .relation(ShapeRelation.INTERSECTS))
                .get();

            assertSearchResponse(searchResponse);
            assertHitCount(searchResponse, 2);
            assertThat(searchResponse.getHits().getAt(0).getId(), not(equalTo("2")));
            assertThat(searchResponse.getHits().getAt(1).getId(), not(equalTo("2")));
        }
        {
            SearchResponse searchResponse = client().prepareSearch(defaultIndexName)
                .setQuery(QueryBuilders.geoShapeQuery(defaultGeoFieldName, multiPolygon)
                    .relation(ShapeRelation.WITHIN))
                .get();

            assertSearchResponse(searchResponse);
            assertThat(searchResponse.getHits().getTotalHits().value, equalTo(2L));
            assertThat(searchResponse.getHits().getHits().length, equalTo(2));
            assertThat(searchResponse.getHits().getAt(0).getId(), not(equalTo("2")));
            assertThat(searchResponse.getHits().getAt(1).getId(), not(equalTo("2")));
        }
        {
            SearchResponse searchResponse = client().prepareSearch(defaultIndexName)
                .setQuery(QueryBuilders.geoShapeQuery(defaultGeoFieldName, multiPolygon)
                    .relation(ShapeRelation.DISJOINT))
                .get();

            assertSearchResponse(searchResponse);
            assertThat(searchResponse.getHits().getTotalHits().value, equalTo(1L));
            assertThat(searchResponse.getHits().getHits().length, equalTo(1));
            assertThat(searchResponse.getHits().getAt(0).getId(), equalTo("2"));
        }
        {
            SearchResponse searchResponse = client().prepareSearch(defaultIndexName)
                .setQuery(QueryBuilders.geoShapeQuery(defaultGeoFieldName, multiPolygon)
                    .relation(ShapeRelation.CONTAINS))
                .get();

            assertSearchResponse(searchResponse);
            assertThat(searchResponse.getHits().getTotalHits().value, equalTo(0L));
            assertThat(searchResponse.getHits().getHits().length, equalTo(0));
        }
    }

    public void testIndexPointsRectangle() throws Exception {
        createMapping(defaultIndexName, defaultType, defaultGeoFieldName);
        ensureGreen();

        client().prepareIndex(defaultIndexName,"_doc").setId("1").setSource(jsonBuilder()
            .startObject()
            .field("name", "Document 1")
            .field(defaultGeoFieldName, "POINT(-30 -30)")
            .endObject()).setRefreshPolicy(IMMEDIATE).get();

        client().prepareIndex(defaultIndexName,"_doc").setId("2").setSource(jsonBuilder()
            .startObject()
            .field("name", "Document 2")
            .field(defaultGeoFieldName, "POINT(-45 -50)")
            .endObject()).setRefreshPolicy(IMMEDIATE).get();

        Rectangle rectangle = new Rectangle(-50, -40, -45, -55);

        SearchResponse searchResponse = client().prepareSearch(defaultIndexName)
            .setQuery(QueryBuilders.geoShapeQuery(defaultGeoFieldName, rectangle)
                .relation(ShapeRelation.INTERSECTS))
            .get();

        assertSearchResponse(searchResponse);
        assertHitCount(searchResponse, 1);
        assertThat(searchResponse.getHits().getAt(0).getId(), equalTo("2"));
    }

    public void testIndexPointsIndexedRectangle() throws Exception {
        createMapping(defaultIndexName, defaultType, defaultGeoFieldName);
        ensureGreen();

        client().prepareIndex(defaultIndexName,"_doc").setId("point1").setSource(jsonBuilder()
            .startObject()
            .field(defaultGeoFieldName, "POINT(-30 -30)")
            .endObject()).setRefreshPolicy(IMMEDIATE).get();

        client().prepareIndex(defaultIndexName,"_doc").setId("point2").setSource(jsonBuilder()
            .startObject()
            .field(defaultGeoFieldName, "POINT(-45 -50)")
            .endObject()).setRefreshPolicy(IMMEDIATE).get();

        String indexedShapeIndex = "indexed_query_shapes";
        String indexedShapePath = "shape";
        XContentBuilder xcb = XContentFactory.jsonBuilder().startObject()
            .startObject("properties").startObject(indexedShapePath)
            .field("type", "geo_shape")
            .endObject()
            .endObject()
            .endObject();
        client().admin().indices().prepareCreate(indexedShapeIndex).addMapping(defaultIndexName, xcb).get();
        ensureGreen();

        client().prepareIndex(indexedShapeIndex,"_doc").setId("shape1").setSource(jsonBuilder()
            .startObject()
            .field(indexedShapePath, "BBOX(-50, -40, -45, -55)")
            .endObject()).setRefreshPolicy(IMMEDIATE).get();

        client().prepareIndex(indexedShapeIndex,"_doc").setId("shape2").setSource(jsonBuilder()
            .startObject()
            .field(indexedShapePath, "BBOX(-60, -50, -50, -60)")
            .endObject()).setRefreshPolicy(IMMEDIATE).get();

        SearchResponse searchResponse = client().prepareSearch(defaultIndexName)
            .setQuery(QueryBuilders.geoShapeQuery(defaultGeoFieldName, "shape1")
                .relation(ShapeRelation.INTERSECTS)
                .indexedShapeIndex(indexedShapeIndex)
                .indexedShapePath(indexedShapePath))
            .get();

        assertSearchResponse(searchResponse);
        assertHitCount(searchResponse, 1);
        assertThat(searchResponse.getHits().getAt(0).getId(), equalTo("point2"));

        searchResponse = client().prepareSearch(defaultIndexName)
            .setQuery(QueryBuilders.geoShapeQuery(defaultGeoFieldName, "shape2")
                .relation(ShapeRelation.INTERSECTS)
                .indexedShapeIndex(indexedShapeIndex)
                .indexedShapePath(indexedShapePath))
            .get();
        assertSearchResponse(searchResponse);
        assertHitCount(searchResponse, 0);
    }

    public void testRectangleSpanningDateline() throws Exception {
        createMapping(defaultIndexName, defaultType, defaultGeoFieldName);
        ensureGreen();

        client().prepareIndex(defaultIndexName,"_doc").setId("1").setSource(jsonBuilder()
            .startObject()
            .field(defaultGeoFieldName, "POINT(-169 0)")
            .endObject()).setRefreshPolicy(IMMEDIATE).get();

        client().prepareIndex(defaultIndexName,"_doc").setId("2").setSource(jsonBuilder()
            .startObject()
            .field(defaultGeoFieldName, "POINT(-179 0)")
            .endObject()).setRefreshPolicy(IMMEDIATE).get();

        client().prepareIndex(defaultIndexName,"_doc").setId("3").setSource(jsonBuilder()
            .startObject()
            .field(defaultGeoFieldName, "POINT(171 0)")
            .endObject()).setRefreshPolicy(IMMEDIATE).get();

        Rectangle rectangle = new Rectangle(169, -178, 1, -1);

        GeoShapeQueryBuilder geoShapeQueryBuilder = QueryBuilders.geoShapeQuery("geo", rectangle);
        SearchResponse response = client().prepareSearch("test").setQuery(geoShapeQueryBuilder).get();
        SearchHits searchHits = response.getHits();
        assertThat(searchHits.getAt(0).getId(),not(equalTo("1")));
        assertThat(searchHits.getAt(1).getId(),not(equalTo("1")));
    }

    public void testPolygonSpanningDateline() throws Exception {
        createMapping(defaultIndexName, defaultType, defaultGeoFieldName);
        ensureGreen();

        client().prepareIndex(defaultIndexName,"_doc").setId("1").setSource(jsonBuilder()
            .startObject()
            .field(defaultGeoFieldName, "POINT(-169 7)")
            .endObject()).setRefreshPolicy(IMMEDIATE).get();

        client().prepareIndex(defaultIndexName,"_doc").setId("2").setSource(jsonBuilder()
            .startObject()
            .field(defaultGeoFieldName, "POINT(-179 7)")
            .endObject()).setRefreshPolicy(IMMEDIATE).get();

        client().prepareIndex(defaultIndexName,"_doc").setId("3").setSource(jsonBuilder()
            .startObject()
            .field(defaultGeoFieldName, "POINT(179 7)")
            .endObject()).setRefreshPolicy(IMMEDIATE).get();

        client().prepareIndex(defaultIndexName,"_doc").setId("4").setSource(jsonBuilder()
            .startObject()
            .field(defaultGeoFieldName, "POINT(171 7)")
            .endObject()).setRefreshPolicy(IMMEDIATE).get();

        Polygon polygon = new Polygon(
            new LinearRing(
                new double[] {-177, 177, 177, -177, -177},
                new double[] {10, 10, 5, 5, 10}
            )
        );

        GeoShapeQueryBuilder geoShapeQueryBuilder = QueryBuilders.geoShapeQuery("geo", polygon);
        geoShapeQueryBuilder.relation(ShapeRelation.INTERSECTS);
        SearchResponse response = client().prepareSearch("test").setQuery(geoShapeQueryBuilder).get();
        SearchHits searchHits = response.getHits();
        assertThat(searchHits.getAt(0).getId(),not(equalTo("1")));
        assertThat(searchHits.getAt(1).getId(),not(equalTo("1")));
        assertThat(searchHits.getAt(0).getId(),not(equalTo("4")));
        assertThat(searchHits.getAt(1).getId(),not(equalTo("4")));
    }

    public void testMultiPolygonSpanningDateline() throws Exception {
        createMapping(defaultIndexName, defaultType, defaultGeoFieldName);
        ensureGreen();

        client().prepareIndex(defaultIndexName,"_doc").setId("1").setSource(jsonBuilder()
            .startObject()
            .field(defaultGeoFieldName, "POINT(-169 7)")
            .endObject()).setRefreshPolicy(IMMEDIATE).get();

        client().prepareIndex(defaultIndexName,"_doc").setId("2").setSource(jsonBuilder()
            .startObject()
            .field(defaultGeoFieldName, "POINT(-179 7)")
            .endObject()).setRefreshPolicy(IMMEDIATE).get();

        client().prepareIndex(defaultIndexName,"_doc").setId("3").setSource(jsonBuilder()
            .startObject()
            .field(defaultGeoFieldName, "POINT(171 7)")
            .endObject()).setRefreshPolicy(IMMEDIATE).get();


        Polygon polygon1 = new Polygon(
            new LinearRing(
                new double[] {-167, -171, 171, -167, -167},
                new double[] {10, 10, 5, 5, 10}
            )
        );

        Polygon polygon2 = new Polygon(
            new LinearRing(
                new double[] {-177, 177, 177, -177, -177},
                new double[] {10, 10, 5, 5, 10}
            )
        );

        MultiPolygon multiPolygon = new MultiPolygon(org.elasticsearch.core.List.of(polygon1, polygon2));

        GeoShapeQueryBuilder geoShapeQueryBuilder = QueryBuilders.geoShapeQuery("geo", multiPolygon);
        geoShapeQueryBuilder.relation(ShapeRelation.INTERSECTS);
        SearchResponse response = client().prepareSearch("test").setQuery(geoShapeQueryBuilder).get();
        SearchHits searchHits = response.getHits();
        assertThat(searchHits.getAt(0).getId(),not(equalTo("3")));
        assertThat(searchHits.getAt(1).getId(),not(equalTo("3")));
    }

    public void testWithInQueryLine() throws Exception {
        createMapping(defaultIndexName, defaultType, defaultGeoFieldName);
        ensureGreen();

        Line line = new Line(new double[]{-25, -35}, new double[]{-25, -35});

        try {
            client().prepareSearch("test")
                .setQuery(QueryBuilders.geoShapeQuery(defaultGeoFieldName, line).relation(ShapeRelation.WITHIN)).get();
        } catch (
            SearchPhaseExecutionException e) {
            assertThat(e.getCause().getMessage(),
                containsString("Field [geo] found an unsupported shape Line"));
        }
    }

    public void testQueryWithinMultiLine() throws Exception {
        createMapping(defaultIndexName, defaultType, defaultGeoFieldName);
        ensureGreen();

        Line lsb1 = new Line(new double[]{-35, -25}, new double[] {-35, -25});
        Line lsb2 = new Line(new double[]{-15, -5}, new double[] {-15, -5});

        MultiLine multiline = new MultiLine(org.elasticsearch.core.List.of(lsb1, lsb2));

        try {
            client().prepareSearch(defaultIndexName)
                .setQuery(QueryBuilders.geoShapeQuery(defaultGeoFieldName, multiline).relation(ShapeRelation.WITHIN)).get();
        } catch (
            SearchPhaseExecutionException e) {
            assertThat(e.getCause().getMessage(),
                containsString("Field [" + defaultGeoFieldName + "] found an unsupported shape Line"));
        }
    }

    public void testQueryLinearRing() throws Exception {
        createMapping(defaultIndexName, defaultType, defaultGeoFieldName);
        ensureGreen();

        LinearRing linearRing = new LinearRing(new double[]{-25, -35, -25}, new double[]{-25, -35, -25});

        // LinearRing extends Line implements Geometry: expose the build process
        GeoShapeQueryBuilder queryBuilder = new GeoShapeQueryBuilder(defaultGeoFieldName, linearRing);
        SearchRequestBuilder searchRequestBuilder = new SearchRequestBuilder(client(), SearchAction.INSTANCE);
        searchRequestBuilder.setQuery(queryBuilder);
        searchRequestBuilder.setIndices("test");
        SearchPhaseExecutionException e = expectThrows(SearchPhaseExecutionException.class, searchRequestBuilder::get);
        assertThat(e.getCause().getMessage(), containsString("LinearRing"));
    }

    public void testQueryPoint() throws Exception {
        createMapping(defaultIndexName, defaultType, defaultGeoFieldName);
        ensureGreen();

        client().prepareIndex(defaultIndexName, defaultType).setId("1").setSource(jsonBuilder()
            .startObject()
            .field(defaultGeoFieldName, "POINT(-35 -25)")
            .endObject()).setRefreshPolicy(IMMEDIATE).get();

        Point point = new Point(-35, -25);
        {
            SearchResponse response = client().prepareSearch("test")
                .setQuery(QueryBuilders.geoShapeQuery(defaultGeoFieldName, point)).get();
            SearchHits searchHits = response.getHits();
            assertEquals(1, searchHits.getTotalHits().value);
        }
        {
            SearchResponse response = client().prepareSearch("test")
                .setQuery(QueryBuilders.geoShapeQuery(defaultGeoFieldName, point).relation(ShapeRelation.WITHIN)).get();
            SearchHits searchHits = response.getHits();
            assertEquals(1, searchHits.getTotalHits().value);
        }
        {
            SearchResponse response = client().prepareSearch("test")
                .setQuery(QueryBuilders.geoShapeQuery(defaultGeoFieldName, point).relation(ShapeRelation.CONTAINS)).get();
            SearchHits searchHits = response.getHits();
            assertEquals(1, searchHits.getTotalHits().value);
        }
        {
            SearchResponse response = client().prepareSearch("test")
                .setQuery(QueryBuilders.geoShapeQuery(defaultGeoFieldName, point).relation(ShapeRelation.DISJOINT)).get();
            SearchHits searchHits = response.getHits();
            assertEquals(0, searchHits.getTotalHits().value);
        }
    }

    public void testQueryMultiPoint() throws Exception {
        createMapping(defaultIndexName, defaultType, defaultGeoFieldName);
        ensureGreen();

        client().prepareIndex(defaultIndexName, defaultType).setId("1").setSource(jsonBuilder()
            .startObject()
            .field(defaultGeoFieldName, "POINT(-35 -25)")
            .endObject()).setRefreshPolicy(IMMEDIATE).get();

        MultiPoint multiPoint = new MultiPoint(org.elasticsearch.core.List.of(new Point(-35,-25), new Point(-15,-5)));

        {
            SearchResponse response = client().prepareSearch("test")
                .setQuery(QueryBuilders.geoShapeQuery(defaultGeoFieldName, multiPoint)).get();
            SearchHits searchHits = response.getHits();
            assertEquals(1, searchHits.getTotalHits().value);
        }
        {
            SearchResponse response = client().prepareSearch("test")
                .setQuery(QueryBuilders.geoShapeQuery(defaultGeoFieldName, multiPoint).relation(ShapeRelation.WITHIN)).get();
            SearchHits searchHits = response.getHits();
            assertEquals(1, searchHits.getTotalHits().value);
        }
        {
            SearchResponse response = client().prepareSearch("test")
                .setQuery(QueryBuilders.geoShapeQuery(defaultGeoFieldName, multiPoint).relation(ShapeRelation.CONTAINS)).get();
            SearchHits searchHits = response.getHits();
            assertEquals(0, searchHits.getTotalHits().value);
        }
        {
            SearchResponse response = client().prepareSearch("test")
                .setQuery(QueryBuilders.geoShapeQuery(defaultGeoFieldName, multiPoint).relation(ShapeRelation.DISJOINT)).get();
            SearchHits searchHits = response.getHits();
            assertEquals(0, searchHits.getTotalHits().value);
        }
    }
}
