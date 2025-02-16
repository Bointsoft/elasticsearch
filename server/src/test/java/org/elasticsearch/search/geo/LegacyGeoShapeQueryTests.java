/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0 and the Server Side Public License, v 1; you may not use this file except
 * in compliance with, at your election, the Elastic License 2.0 or the Server
 * Side Public License, v 1.
 */

package org.elasticsearch.search.geo;

import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.common.Strings;
import org.elasticsearch.common.geo.GeoJson;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentFactory;
import org.elasticsearch.common.xcontent.XContentType;
import org.elasticsearch.geo.GeometryTestUtils;
import org.elasticsearch.geometry.Geometry;
import org.elasticsearch.geometry.MultiPoint;
import org.elasticsearch.geometry.Point;
import org.elasticsearch.index.mapper.LegacyGeoShapeFieldMapper;
import org.elasticsearch.index.mapper.MapperParsingException;

import java.io.IOException;

import static org.elasticsearch.action.support.WriteRequest.RefreshPolicy.IMMEDIATE;
import static org.elasticsearch.common.xcontent.XContentFactory.jsonBuilder;
import static org.elasticsearch.index.query.QueryBuilders.geoIntersectionQuery;
import static org.elasticsearch.index.query.QueryBuilders.geoShapeQuery;
import static org.elasticsearch.index.query.QueryBuilders.matchAllQuery;
import static org.hamcrest.Matchers.containsString;

public class LegacyGeoShapeQueryTests extends GeoShapeQueryTestCase {

    private static final String[] PREFIX_TREES = new String[] {
        LegacyGeoShapeFieldMapper.PrefixTrees.GEOHASH,
        LegacyGeoShapeFieldMapper.PrefixTrees.QUADTREE
    };

    @Override
    protected void createMapping(String indexName, String type, String fieldName, Settings settings) throws Exception {
        final XContentBuilder xcb = XContentFactory.jsonBuilder().startObject()
            .startObject("properties").startObject(fieldName)
            .field("type", "geo_shape")
            .field("tree", randomFrom(PREFIX_TREES))
            .endObject()
            .endObject()
            .endObject();
        client().admin().indices().prepareCreate(indexName).addMapping(type, xcb).setSettings(settings).get();
    }

    @Override
    protected boolean forbidPrivateIndexSettings() {
        return false;
    }

    public void testPointsOnlyExplicit() throws Exception {
        String mapping = Strings.toString(XContentFactory.jsonBuilder().startObject()
            .startObject("properties").startObject(defaultGeoFieldName)
            .field("type", "geo_shape")
            .field("tree", randomBoolean() ? "quadtree" : "geohash")
            .field("tree_levels", "6")
            .field("distance_error_pct", "0.01")
            .field("points_only", true)
            .endObject()
            .endObject().endObject());


        client().admin().indices().prepareCreate("geo_points_only").addMapping(defaultType, mapping, XContentType.JSON).get();
        ensureGreen();

        // MULTIPOINT
        MultiPoint multiPoint = GeometryTestUtils.randomMultiPoint(false);
        client().prepareIndex("geo_points_only", defaultType).setId("1")
            .setSource(GeoJson.toXContent(multiPoint, jsonBuilder().startObject().field(defaultGeoFieldName), null).endObject())
            .setRefreshPolicy(IMMEDIATE).get();

        // POINT
        Point point =  GeometryTestUtils.randomPoint(false);
        client().prepareIndex("geo_points_only", defaultType).setId("2")
            .setSource(GeoJson.toXContent(point, jsonBuilder().startObject().field(defaultGeoFieldName), null).endObject())
            .setRefreshPolicy(IMMEDIATE).get();

        // test that point was inserted
        SearchResponse response = client().prepareSearch("geo_points_only")
            .setQuery(matchAllQuery())
            .get();

        assertEquals(2, response.getHits().getTotalHits().value);
    }

    public void testPointsOnly() throws Exception {
        String mapping = Strings.toString(XContentFactory.jsonBuilder().startObject()
            .startObject("properties").startObject(defaultGeoFieldName)
            .field("type", "geo_shape")
            .field("tree", randomBoolean() ? "quadtree" : "geohash")
            .field("tree_levels", "6")
            .field("distance_error_pct", "0.01")
            .field("points_only", true)
            .endObject()
            .endObject().endObject());

        client().admin().indices().prepareCreate("geo_points_only").addMapping(defaultType, mapping, XContentType.JSON).get();
        ensureGreen();

        Geometry geometry = GeometryTestUtils.randomGeometry(false);
        try {
            client().prepareIndex("geo_points_only", defaultType).setId("1")
                .setSource(GeoJson.toXContent(geometry, jsonBuilder().startObject().field(defaultGeoFieldName), null).endObject())
                .setRefreshPolicy(IMMEDIATE).get();
        } catch (MapperParsingException e) {
            // Random geometry generator created something other than a POINT type, verify the correct exception is thrown
            assertThat(e.getMessage(), containsString("is configured for points only"));
            return;
        }

        // test that point was inserted
        SearchResponse response =
            client().prepareSearch("geo_points_only").setQuery(geoIntersectionQuery(defaultGeoFieldName, geometry)).get();
        assertEquals(1, response.getHits().getTotalHits().value);
    }

    public void testFieldAlias() throws IOException {
        String mapping = Strings.toString(XContentFactory.jsonBuilder()
            .startObject()
            .startObject("properties")
            .startObject(defaultGeoFieldName)
            .field("type", "geo_shape")
            .field("tree", randomBoolean() ? "quadtree" : "geohash")
            .endObject()
            .startObject("alias")
            .field("type", "alias")
            .field("path", defaultGeoFieldName)
            .endObject()
            .endObject()
            .endObject());

        client().admin().indices().prepareCreate(defaultIndexName).addMapping(defaultType, mapping, XContentType.JSON).get();
        ensureGreen();

        MultiPoint multiPoint = GeometryTestUtils.randomMultiPoint(false);
        client().prepareIndex(defaultIndexName, defaultType).setId("1")
            .setSource(GeoJson.toXContent(multiPoint, jsonBuilder().startObject().field(defaultGeoFieldName), null).endObject())
            .setRefreshPolicy(IMMEDIATE).get();

        SearchResponse response = client().prepareSearch(defaultIndexName)
            .setQuery(geoShapeQuery("alias", multiPoint))
            .get();
        assertEquals(1, response.getHits().getTotalHits().value);
    }
}
