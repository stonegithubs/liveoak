package org.projectodd.restafari.mongo;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mongodb.BasicDBObject;
import com.mongodb.DB;
import com.mongodb.MongoClient;
import io.netty.handler.codec.http.HttpHeaders;
import org.apache.http.client.methods.*;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.projectodd.restafari.container.DefaultContainer;
import org.projectodd.restafari.container.SimpleConfig;
import org.projectodd.restafari.container.UnsecureServer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Created with IntelliJ IDEA.
 * User: mwringe
 * Date: 18/10/13
 * Time: 10:29 AM
 * To change this template use File | Settings | File Templates.
 */
public class BaseMongoDBTest {

    protected static UnsecureServer server;
    protected static MongoClient mongoClient;
    protected static DB db;

    protected static String baseURL;
    protected static final String TYPE = "storage";

/*    protected String mongoDatabaseName;
    protected Integer mongoPort;
    protected String mongoHost;

    public BaseMongoDBTest() {
        mongoDatabaseName = System.getProperty("mongo.db", "MongoControllerTest_" + UUID.randomUUID());
        mongoPort = new Integer(System.getProperty("mongo.port", "27017"));
        mongoHost = System.getProperty("mongo.host", "localhost");
    }

    @Override
    public RootResource createRootResource() {
        return new MongoDBResource(TYPE);
    }

    @Override
    public Config createConfig() {
        SimpleConfig config = new SimpleConfig();
        config.put("db", this.mongoDatabaseName);
        config.put("port", this.mongoPort);
        config.put("host", this.mongoHost);
        return config;
    }

    @Override
    @Before
    public void setUpContainer() throws Exception {
       super.setUpContainer();

        // configure a local mongo client to verify the data methods
        mongoClient = new MongoClient(this.mongoHost, this.mongoPort);
        db = mongoClient.getDB(this.mongoDatabaseName);
    }

*/

    @BeforeClass
    public static void init() throws Exception {
        String database = System.getProperty("mongo.db", "MongoControllerTest_" + UUID.randomUUID());
        Integer port = new Integer(System.getProperty("mongo.port", "27017"));
        String host = System.getProperty("mongo.host", "localhost");

        // configure the mongo controller
        SimpleConfig config = new SimpleConfig();
        config.put("db", database);
        config.put("port", port);
        config.put("host", host);

        DefaultContainer container = new DefaultContainer();
        container.registerResource(new MongoDBResource(TYPE), config);

        //TODO: pass these params in instead of hardcoding them
        server = new UnsecureServer(container, "localhost", 8080);
        server.start();

        baseURL = "http://localhost:8080/" + TYPE;

        // configure a local mongo client to verify the data methods
        mongoClient = new MongoClient(host, port);
        db = mongoClient.getDB(database);
        db.dropDatabase();
    }

    @AfterClass
    public static void dispose() {
        mongoClient.close();
        if (server == null)
            return;

        try {
            server.stop();
        } catch (InterruptedException ignored) {
        }
    }

    @Test
    public void testGetStorageEmpty() throws Exception {
        //DB db = mongoClient.getDB("testGetStorageEmpty");
        db.dropDatabase(); //TODO: create a new DB here instead of dropping the old one
        assertEquals(0, db.getCollectionNames().size());

        CloseableHttpResponse response = testSimpleGetMethod(baseURL);
        // This should return an empty list since there are no collections
        assertEquals(200, response.getStatusLine().getStatusCode());

        // verify response
        ObjectMapper mapper = new ObjectMapper();
        JsonNode jsonNode = mapper.readTree(response.getEntity().getContent());

        assertEquals(3, jsonNode.size());  // id, _self, members
        assertEquals("storage", jsonNode.get("id").asText());
        assertEquals("/storage", jsonNode.get("_self").get("href").asText());
        assertEquals("collection", jsonNode.get("_self").get("type").asText());
        assertEquals("[]", jsonNode.get("members").toString());
    }

    @Test
    public void testGetStorageCollections() throws Exception {
        //DB db = mongoClient.getDB("testGetStorageCollections");
        db.dropDatabase(); //TODO: create a new DB here instead of dropping the old one
        assertEquals(0, db.getCollectionNames().size());
        // create a couple of collections
        db.createCollection("collection1", new BasicDBObject());
        db.createCollection("collection2", new BasicDBObject());
        db.createCollection("collection3", new BasicDBObject());
        // check that the collections are there (Note: there is an internal index collection, so 4 instead of 3)
        assertEquals(4, db.getCollectionNames().size());

        CloseableHttpResponse response = testSimpleGetMethod(baseURL);
        // This should return an empty list since there are no collections
        assertEquals(200, response.getStatusLine().getStatusCode());

        // verify response
        ObjectMapper mapper = new ObjectMapper();
        JsonNode jsonNode = mapper.readTree(response.getEntity().getContent());

        assertEquals(3, jsonNode.size());  // id, _self, members
        assertEquals("storage", jsonNode.get("id").asText());
        assertEquals("/storage", jsonNode.get("_self").get("href").asText());
        assertEquals("collection", jsonNode.get("_self").get("type").asText());
        assertEquals("{\"id\":\"collection1\",\"_self\":{\"href\":\"/storage/collection1\",\"type\":\"collection\"}}",
                jsonNode.get("members").get(0).toString());
        assertEquals("{\"id\":\"collection2\",\"_self\":{\"href\":\"/storage/collection2\",\"type\":\"collection\"}}",
                jsonNode.get("members").get(1).toString());
        assertEquals("{\"id\":\"collection3\",\"_self\":{\"href\":\"/storage/collection3\",\"type\":\"collection\"}}",
                jsonNode.get("members").get(2).toString());
    }

    protected CloseableHttpResponse testSimpleGetMethod(String url) throws Exception {
        return testSimpleGetMethod(url, "application/json", "application/json");
    }

    protected CloseableHttpResponse testSimpleGetMethod(String url, String contentType_header, String accept_header) throws Exception {
        HttpGet get = new HttpGet(url);
        get.setHeader(HttpHeaders.Names.CONTENT_TYPE, contentType_header);
        get.setHeader(HttpHeaders.Names.ACCEPT, accept_header);

        CloseableHttpClient httpClient = HttpClientBuilder.create().build();
        return httpClient.execute(get);
    }

    protected CloseableHttpResponse testSimplePostMethod(String url, String content) throws Exception {
        return testSimplePostMethod(url, content, "application/json", "application/json");
    }

    protected CloseableHttpResponse testSimplePostMethod(String url, String content, String contentType_header, String accept_header) throws Exception {
        HttpPost post = new HttpPost(url);
        post.setHeader(HttpHeaders.Names.CONTENT_TYPE, contentType_header);
        post.setHeader(HttpHeaders.Names.ACCEPT, accept_header);

        StringEntity entity = new StringEntity(content, ContentType.create("text/plain", "UTF-8"));
        post.setEntity(entity);

        CloseableHttpClient httpClient = HttpClientBuilder.create().build();
        return httpClient.execute(post);
    }

    protected CloseableHttpResponse testSimpleDeleteMethod(String url) throws Exception {
        return testSimpleDeleteMethod(url, "application/json", "application/json");
    }

    protected CloseableHttpResponse testSimpleDeleteMethod(String url, String contentType_header, String accept_header) throws Exception {
        HttpDelete delete = new HttpDelete(url);
        delete.setHeader(HttpHeaders.Names.CONTENT_TYPE, contentType_header);
        delete.setHeader(HttpHeaders.Names.ACCEPT, accept_header);

        CloseableHttpClient httpClient = HttpClientBuilder.create().build();
        return httpClient.execute(delete);
    }

    protected CloseableHttpResponse testSimplePutMethod(String url, String content) throws Exception {
        return testSimplePutMethod(url, content, "application/json", "application/json");
    }

    protected CloseableHttpResponse testSimplePutMethod(String url, String content, String contentType_header, String accept_header) throws Exception {
        HttpPut put = new HttpPut(url);
        put.setHeader(HttpHeaders.Names.CONTENT_TYPE, contentType_header);
        put.setHeader(HttpHeaders.Names.ACCEPT, accept_header);

        StringEntity entity = new StringEntity(content, ContentType.create("text/plain", "UTF-8"));
        put.setEntity(entity);

        CloseableHttpClient httpClient = HttpClientBuilder.create().build();
        return httpClient.execute(put);
    }

/*
    protected Map<String, CollectionResource> getCollectionResources(Resource resource) {
        TestResourceSink resourceSink = new TestResourceSink();

        if (resource instanceof CollectionResource) {
            ((CollectionResource)resource).writeMembers(resourceSink);
        } else if (resource instanceof ObjectResource) {
            ((ObjectResource)resource).writeMembers(resourceSink);
        } else {
            fail();
        }

        assertEquals(resourceSink.getCollectionResources().size(), resourceSink.getResources().size());
        return resourceSink.getCollectionResources();
    }

    protected Map<String, ObjectResource> getObjectResources(Resource resource) {
        TestResourceSink resourceSink = new TestResourceSink();

        if (resource instanceof CollectionResource) {
            ((CollectionResource)resource).writeMembers(resourceSink);
        } else if (resource instanceof ObjectResource) {
            ((ObjectResource)resource).writeMembers(resourceSink);
        } else {
            fail();
        }

        assertTrue(resourceSink.getObjectResources().size() == resourceSink.getResources().size());
        return resourceSink.getObjectResources();
    }


    protected Map<String, PropertyResource> getPropertyResources(Resource resource) {
        TestResourceSink resourceSink = new TestResourceSink();

        if (resource instanceof CollectionResource) {
            ((CollectionResource)resource).writeMembers(resourceSink);
        } else if (resource instanceof ObjectResource) {
            ((ObjectResource)resource).writeMembers(resourceSink);
        } else {
            fail();
        }

        assertTrue(resourceSink.getPropertyResources().size() == resourceSink.getResources().size());
        return resourceSink.getPropertyResources();
    }

    protected Map<String, Resource> getResources(Resource resource) {
        TestResourceSink resourceSink = new TestResourceSink();

        if (resource instanceof CollectionResource) {
            ((CollectionResource)resource).writeMembers(resourceSink);
        } else if (resource instanceof ObjectResource) {
            ((ObjectResource)resource).writeMembers(resourceSink);
        } else {
            fail();
        }

        return resourceSink.getResources();
    }

    protected class TestResourceSink implements ResourceSink {

        protected Map<String, Resource> resources = new HashMap<String, Resource>();
        protected Map<String, CollectionResource> collectionResources = new HashMap<String, CollectionResource>();
        protected Map<String, ObjectResource> objectResources = new HashMap<String, ObjectResource>();
        protected Map<String, PropertyResource> propertyResources = new HashMap<String, PropertyResource>();

        @Override
        public void close() {
            //To change body of implemented methods use File | Settings | File Templates.
        }

        @Override
        public void accept(Resource resource) {
            this.resources.put(resource.id(), resource);
            if (resource instanceof CollectionResource) {
                this.collectionResources.put(resource.id(), (CollectionResource) resource);
            } else if (resource instanceof ObjectResource) {
                this.objectResources.put(resource.id(), (ObjectResource) resource);
            } else if (resource instanceof PropertyResource) {
                this.propertyResources.put(resource.id(), (PropertyResource) resource);
            }
        }

        public Map<String, Resource> getResources() {
            return this.resources;
        }

        public Map<String, CollectionResource> getCollectionResources() {
            return this.collectionResources;
        }

        public Map<String, ObjectResource> getObjectResources() {
            return this.objectResources;
        }

        public Map<String, PropertyResource> getPropertyResources() {
            return this.propertyResources;
        }
    }

*/


}
