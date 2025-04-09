package com.darkcell.burpn2neo;

import burp.api.montoya.MontoyaApi;
import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.http.message.responses.HttpResponse;
import burp.api.montoya.logging.Logging;
import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;
import org.neo4j.driver.Result;
import org.neo4j.driver.Session;
import org.neo4j.driver.Record;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.neo4j.driver.Values.parameters;

/**
 * Manages Neo4j database connections and operations for the Burp extension.
 * Handles data processing, storage, and retrieval from Neo4j.
 */
public class Neo4jManager implements AutoCloseable {
    private final MontoyaApi api;
    private final Logging logging;
    private final AtomicBoolean processInScopeOnly = new AtomicBoolean(true);
    
    // Neo4j connection parameters
    private String neo4jUri = "bolt://localhost:7687";
    private String neo4jUsername = "neo4j";
    private String neo4jPassword = "password";
    private boolean connected = false;
    private Driver driver;

    public Neo4jManager(MontoyaApi api) {
        this.api = api;
        this.logging = api.logging();
    }

    /**
     * Initializes the Neo4j connection and prepares for data processing.
     * @return true if connection was successful, false otherwise
     */
    public boolean initialize() {
        try {
            // TODO: Implement actual Neo4j driver connection
            logging.logToOutput("Neo4j Manager initialized. Connection to Neo4j will be established when needed.");
            connected = true;
            return true;
        } catch (Exception e) {
            logging.logToError("Failed to initialize Neo4j Manager: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Process HTTP request/response pairs to extract and store data in Neo4j.
     * @param requestResponse The HTTP request/response pair to process
     */
    public void processRequestResponse(HttpRequestResponse requestResponse) {
        if (!connected) {
            logging.logToOutput("Not connected to Neo4j, skipping processing");
            return;
        }

        try {
            HttpRequest request = requestResponse.request();
            HttpResponse response = requestResponse.response();
            
            if (response == null) {
                // Skip requests with no response
                return;
            }

            String url = request.url();
            URI uri = new URI(url);
            String host = uri.getHost();
            String path = uri.getPath();
            
            // TODO: Extract parameters, headers, etc.
            // TODO: Store data in Neo4j using appropriate Cypher queries
            
            logging.logToOutput("Processed: " + host + path);
        } catch (URISyntaxException e) {
            logging.logToError("Invalid URL in request: " + e.getMessage());
        } catch (Exception e) {
            logging.logToError("Error processing request/response: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Closes Neo4j connection and performs cleanup.
     */
    public void shutdown() {
        try {
            // TODO: Close Neo4j driver connection
            logging.logToOutput("Neo4j Manager shutdown complete");
        } catch (Exception e) {
            logging.logToError("Error during Neo4j Manager shutdown: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Check if only in-scope items should be processed.
     * @return true if only processing in-scope items, false otherwise
     */
    public boolean isProcessInScopeOnly() {
        return processInScopeOnly.get();
    }

    /**
     * Set whether to process only in-scope items.
     * @param processInScopeOnly true to process only in-scope items, false to process all
     */
    public void setProcessInScopeOnly(boolean processInScopeOnly) {
        this.processInScopeOnly.set(processInScopeOnly);
    }

    /**
     * Update Neo4j connection parameters.
     * @param uri Neo4j server URI
     * @param username Neo4j username
     * @param password Neo4j password
     */
    public void updateConnectionParams(String uri, String username, String password) {
        this.neo4jUri = uri;
        this.neo4jUsername = username;
        this.neo4jPassword = password;
    }

    /**
     * Connect to the Neo4j database
     */
    public boolean connect(String uri, String username, String password) {
        try {
            if (driver != null) {
                driver.close();
            }

            this.neo4jUri = uri;
            this.neo4jUsername = username;
            this.neo4jPassword = password;

            driver = GraphDatabase.driver(uri, AuthTokens.basic(username, password));
            // Test connection
            driver.verifyConnectivity();
            this.connected = true;

            // Initialize database schema
            initializeSchema();

            logging.logToOutput("Connected to Neo4j at " + uri);
            return true;
        } catch (Exception e) {
            logging.logToError("Failed to connect to Neo4j: " + e.getMessage());
            this.connected = false;
            return false;
        }
    }

    /**
     * Initialize the Neo4j schema with constraints and indexes
     */
    private void initializeSchema() {
        try (Session session = driver.session()) {
            // Create constraint for Host nodes
            session.run("CREATE CONSTRAINT host_name IF NOT EXISTS FOR (h:Host) REQUIRE h.name IS UNIQUE");
            
            // Create constraint for Endpoint nodes
            session.run("CREATE CONSTRAINT endpoint_path IF NOT EXISTS FOR (e:Endpoint) REQUIRE (e.host, e.path, e.method) IS NODE KEY");
            
            // Create index for Parameter nodes
            session.run("CREATE INDEX parameter_name IF NOT EXISTS FOR (p:Parameter) ON (p.name)");
        } catch (Exception e) {
            logging.logToError("Failed to initialize Neo4j schema: " + e.getMessage());
        }
    }

    /**
     * Add or update a host in the database
     */
    public void upsertHost(String hostName) {
        if (!connected || driver == null) return;
        
        try (Session session = driver.session()) {
            session.executeWrite(tx -> {
                tx.run("MERGE (h:Host {name: $host}) " +
                       "ON CREATE SET h.firstSeen = datetime() " +
                       "ON MATCH SET h.lastSeen = datetime()",
                       parameters("host", hostName));
                return null;
            });
        } catch (Exception e) {
            logging.logToError("Error upserting host: " + e.getMessage());
        }
    }

    /**
     * Add or update an endpoint in the database
     */
    public void upsertEndpoint(String hostName, String path, String method) {
        if (!connected || driver == null) return;
        
        try (Session session = driver.session()) {
            session.executeWrite(tx -> {
                tx.run("MATCH (h:Host {name: $host}) " +
                       "MERGE (e:Endpoint {host: $host, path: $path, method: $method}) " +
                       "MERGE (h)-[:HAS_ENDPOINT]->(e)",
                       parameters("host", hostName, "path", path, "method", method));
                return null;
            });
        } catch (Exception e) {
            logging.logToError("Error upserting endpoint: " + e.getMessage());
        }
    }

    /**
     * Add or update a parameter in the database
     */
    public void upsertParameter(String hostName, String path, String paramName, String paramValue) {
        if (!connected || driver == null) return;
        
        try (Session session = driver.session()) {
            session.executeWrite(tx -> {
                tx.run("MATCH (e:Endpoint {host: $host, path: $path}) " +
                       "MERGE (p:Parameter {name: $name}) " +
                       "ON CREATE SET p.values = [$value] " +
                       "ON MATCH SET p.values = p.values + $value " +
                       "MERGE (e)-[:HAS_PARAMETER]->(p)",
                       parameters("host", hostName, "path", path, 
                                 "name", paramName, "value", paramValue));
                return null;
            });
        } catch (Exception e) {
            logging.logToError("Error upserting parameter: " + e.getMessage());
        }
    }

    /**
     * Find similar endpoints across different hosts
     */
    public List<Record> findSimilarEndpoints() {
        if (!connected || driver == null) return List.of();
        
        try (Session session = driver.session()) {
            Result result = session.run(
                "MATCH (e1:Endpoint)-[:HAS_PARAMETER]->(p:Parameter)<-[:HAS_PARAMETER]-(e2:Endpoint) " +
                "WHERE e1.host <> e2.host " +
                "RETURN e1.host as host1, e1.path as path1, " +
                "       e2.host as host2, e2.path as path2, " +
                "       p.name as parameter " +
                "ORDER BY parameter " +
                "LIMIT 100"
            );
            
            return result.list();
        } catch (Exception e) {
            logging.logToError("Error finding similar endpoints: " + e.getMessage());
            return List.of();
        }
    }

    /**
     * Get all hosts in the database
     */
    public List<Record> getAllHosts() {
        if (!connected || driver == null) return List.of();
        
        try (Session session = driver.session()) {
            Result result = session.run("MATCH (h:Host) RETURN h.name as host ORDER BY host");
            return result.list();
        } catch (Exception e) {
            logging.logToError("Error getting hosts: " + e.getMessage());
            return List.of();
        }
    }

    /**
     * Get all endpoints for a specific host
     */
    public List<Record> getEndpointsForHost(String hostName) {
        if (!connected || driver == null) return List.of();
        
        try (Session session = driver.session()) {
            Result result = session.run(
                "MATCH (h:Host {name: $host})-[:HAS_ENDPOINT]->(e:Endpoint) " +
                "RETURN e.path as path, e.method as method " +
                "ORDER BY path",
                parameters("host", hostName)
            );
            return result.list();
        } catch (Exception e) {
            logging.logToError("Error getting endpoints: " + e.getMessage());
            return List.of();
        }
    }

    /**
     * Get all parameters for a specific endpoint
     */
    public List<Record> getParametersForEndpoint(String hostName, String path) {
        if (!connected || driver == null) return List.of();
        
        try (Session session = driver.session()) {
            Result result = session.run(
                "MATCH (e:Endpoint {host: $host, path: $path})-[:HAS_PARAMETER]->(p:Parameter) " +
                "RETURN p.name as name, p.values as values " +
                "ORDER BY name",
                parameters("host", hostName, "path", path)
            );
            return result.list();
        } catch (Exception e) {
            logging.logToError("Error getting parameters: " + e.getMessage());
            return List.of();
        }
    }

    /**
     * Import proxy history into the database
     */
    public void importProxyHistory(List<Object> history, ProgressCallback callback) {
        if (!connected || driver == null) return;
        
        int total = history.size();
        int count = 0;
        
        for (Object item : history) {
            try {
                // Handle the item based on its actual type
                // This is a generic handler to work with multiple Burp API versions
                String host = "";
                String method = "";
                String url = "";
                List<?> parameters = List.of();
                
                // Extract data from the request object using reflection to handle different API versions
                try {
                    // Get the request object (method name might differ based on API version)
                    Object request = null;
                    
                    if (item.getClass().getMethod("request").getReturnType() != void.class) {
                        request = item.getClass().getMethod("request").invoke(item);
                    } else if (item.getClass().getMethod("finalRequest").getReturnType() != void.class) {
                        request = item.getClass().getMethod("finalRequest").invoke(item);
                    }
                    
                    if (request != null) {
                        // Get the HTTP service
                        Object httpService = request.getClass().getMethod("httpService").invoke(request);
                        host = (String) httpService.getClass().getMethod("host").invoke(httpService);
                        
                        // Get method and URL
                        method = (String) request.getClass().getMethod("method").invoke(request);
                        url = (String) request.getClass().getMethod("url").invoke(request);
                        
                        // Get parameters
                        parameters = (List<?>) request.getClass().getMethod("parameters").invoke(request);
                    }
                } catch (Exception e) {
                    logging.logToError("Error extracting data: " + e.getMessage());
                    continue;
                }
                
                // Extract path from URL
                String path;
                try {
                    String extractedPath = new java.net.URL(url).getPath();
                    path = extractedPath.isEmpty() ? "/" : extractedPath;
                } catch (Exception e) {
                    path = url;
                }
                
                // Add to database
                final String finalHost = host;
                final String finalMethod = method;
                final String finalPath = path;
                
                upsertHost(finalHost);
                upsertEndpoint(finalHost, finalPath, finalMethod);
                
                // Process parameters
                for (Object param : parameters) {
                    try {
                        String name = (String) param.getClass().getMethod("name").invoke(param);
                        String value = (String) param.getClass().getMethod("value").invoke(param);
                        upsertParameter(finalHost, finalPath, name, value);
                    } catch (Exception e) {
                        logging.logToError("Error processing parameter: " + e.getMessage());
                    }
                }
                
                count++;
                if (callback != null) {
                    callback.onProgress(count, total);
                }
                
            } catch (Exception e) {
                logging.logToError("Error processing history item: " + e.getMessage());
            }
        }
    }

    /**
     * Get all hosts with endpoint statistics 
     */
    public List<Record> getAllHostsWithStats() {
        if (!connected || driver == null) return List.of();
        
        try (Session session = driver.session()) {
            Result result = session.run(
                "MATCH (h:Host) " +
                "OPTIONAL MATCH (h)-[:HAS_ENDPOINT]->(e:Endpoint) " +
                "WITH h, COUNT(e) as endpointCount " +
                "RETURN h.name as host, endpointCount " +
                "ORDER BY host"
            );
            return result.list();
        } catch (Exception e) {
            logging.logToError("Error getting hosts with stats: " + e.getMessage());
            return List.of();
        }
    }

    /**
     * Get all endpoints for a host with parameter statistics
     */
    public List<Record> getEndpointsForHostWithStats(String hostName) {
        if (!connected || driver == null) return List.of();
        
        try (Session session = driver.session()) {
            Result result = session.run(
                "MATCH (h:Host {name: $host})-[:HAS_ENDPOINT]->(e:Endpoint) " +
                "OPTIONAL MATCH (e)-[:HAS_PARAMETER]->(p:Parameter) " +
                "WITH e, COUNT(p) as parameterCount " +
                "RETURN e.path as path, e.method as method, parameterCount " +
                "ORDER BY path",
                parameters("host", hostName)
            );
            return result.list();
        } catch (Exception e) {
            logging.logToError("Error getting endpoints with stats: " + e.getMessage());
            return List.of();
        }
    }

    /**
     * Find similar endpoints by path structure across different hosts
     */
    public List<Record> findSimilarEndpointsByPath() {
        if (!connected || driver == null) return List.of();
        
        try (Session session = driver.session()) {
            Result result = session.run(
                "MATCH (e1:Endpoint), (e2:Endpoint) " +
                "WHERE e1.host <> e2.host AND e1.path = e2.path " +
                "RETURN e1.host as host1, e1.path as path1, e1.method as method1, " +
                "       e2.host as host2, e2.path as path2, e2.method as method2 " +
                "ORDER BY path1 " +
                "LIMIT 100"
            );
            return result.list();
        } catch (Exception e) {
            logging.logToError("Error finding similar endpoints by path: " + e.getMessage());
            return List.of();
        }
    }

    /**
     * Executes a custom Cypher query and returns the results
     * 
     * @param query The Cypher query to execute
     * @return List of records returned by the query
     */
    public List<Record> executeQuery(String query) {
        if (!connected || driver == null) return List.of();
        
        try (Session session = driver.session()) {
            Result result = session.run(query);
            return result.list();
        } catch (Exception e) {
            logging.logToError("Error executing custom query: " + e.getMessage());
            throw new RuntimeException("Query execution failed: " + e.getMessage(), e);
        }
    }

    /**
     * Find endpoints with similar parameter names across different hosts
     */
    public List<Record> findSimilarParameters() {
        if (!connected || driver == null) return List.of();
        
        try (Session session = driver.session()) {
            Result result = session.run(
                "MATCH (e1:Endpoint)-[:HAS_PARAMETER]->(p1:Parameter), " +
                "      (e2:Endpoint)-[:HAS_PARAMETER]->(p2:Parameter) " +
                "WHERE e1.host <> e2.host AND p1.name = p2.name " +
                "RETURN e1.host as host1, e1.path as path1, " +
                "       e2.host as host2, e2.path as path2, " +
                "       p1.name as parameter " +
                "ORDER BY parameter " +
                "LIMIT 100"
            );
            return result.list();
        } catch (Exception e) {
            logging.logToError("Error finding similar parameters: " + e.getMessage());
            return List.of();
        }
    }

    /**
     * Find endpoints with similar API patterns (path structure) across different hosts
     */
    public List<Record> findSimilarAPIPatterns() {
        if (!connected || driver == null) return List.of();
        
        try (Session session = driver.session()) {
            // Find similar API patterns using path structure similarity
            // This example looks for common path segments
            Result result = session.run(
                "MATCH (e1:Endpoint), (e2:Endpoint) " +
                "WHERE e1.host <> e2.host " +
                "AND e1.path CONTAINS '/api/' AND e2.path CONTAINS '/api/' " +
                "AND apoc.text.levenshteinSimilarity(e1.path, e2.path) > 0.6 " +
                "AND e1.path <> e2.path " +
                "RETURN e1.host as host1, e1.path as path1, e1.method as method1, " +
                "       e2.host as host2, e2.path as path2, e2.method as method2, " +
                "       apoc.text.levenshteinSimilarity(e1.path, e2.path) as similarity " +
                "ORDER BY similarity DESC " +
                "LIMIT 100"
            );
            return result.list();
        } catch (Exception e) {
            // If APOC extension is not available, try a simpler match
            try (Session session = driver.session()) {
                Result result = session.run(
                    "MATCH (e1:Endpoint), (e2:Endpoint) " +
                    "WHERE e1.host <> e2.host " +
                    "AND e1.path CONTAINS '/api/' AND e2.path CONTAINS '/api/' " +
                    "AND e1.method = e2.method " +
                    "AND e1.path <> e2.path " +
                    "RETURN e1.host as host1, e1.path as path1, e1.method as method1, " +
                    "       e2.host as host2, e2.path as path2, e2.method as method2 " +
                    "LIMIT 100"
                );
                return result.list();
            } catch (Exception ex) {
                logging.logToError("Error finding similar API patterns: " + ex.getMessage());
                return List.of();
            }
        }
    }

    @Override
    public void close() {
        if (driver != null) {
            driver.close();
            driver = null;
            connected = false;
        }
    }

    public boolean isConnected() {
        return connected && driver != null;
    }

    public String getUri() {
        return neo4jUri;
    }

    public String getUsername() {
        return neo4jUsername;
    }

    /**
     * Callback interface for tracking import progress
     */
    public interface ProgressCallback {
        void onProgress(int current, int total);
    }
}