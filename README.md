This extension allows you to analyze Burp Suite proxy traffic using Neo4j graph database. It captures all hosts, endpoints, and parameters from your web traffic and helps identify relationships between them.

## Features

- Captures proxy traffic in real-time and stores it in Neo4j
- Imports existing proxy history
- Finds similar endpoints and parameters across different hosts
- Visualizes relationships between hosts, endpoints, and parameters
- Helps identify similar backend endpoints even when exposed through different front-end URLs

## Setup Instructions

### 1. Install Neo4j

1. Download and install [Neo4j Desktop](https://neo4j.com/download/)
2. Create a new database:
   - Open Neo4j Desktop
   - Click "+ Add" → "Local DBMS"
   - Enter a name (e.g., "BurpAnalyzer")
   - Set a password
   - Click "Create"
3. Start the database by clicking "Start"
4. Note the connection details (by default: `bolt://localhost:7687`)

### 2. Install the Extension in Burp Suite

1. Download the latest release JAR file from the releases page
2. Open Burp Suite Professional
3. Go to "Extensions" tab → "Extensions settings"
4. Click "Add" in the "Burp Extensions" section
5. Extension type: Java
6. Extension file: Select the downloaded JAR file
7. Click "Next" to load the extension

### 3. Configure the Extension

1. Go to the "Neo4j Graph" tab in Burp Suite
2. Enter your Neo4j connection details:
   - URI: bolt://localhost:7687 (or your custom URI)
   - Username: neo4j (default)
   - Password: (your password)
3. Click "Connect to Neo4j"
4. You should see "Status: Connected to..." if successful

## Usage

### Capturing Live Traffic

Once connected, the extension automatically captures all proxy traffic and stores it in the Neo4j database.

### Importing Existing Proxy History

1. Navigate to the "Import" tab
2. Click "Import Proxy History"
3. Wait for the import to complete (progress is displayed)

### Analyzing Relationships

1. Navigate to the "Analysis" tab
2. Click "Refresh Hosts" to load hosts
3. Click "Find Similar Endpoints" to identify relationships between hosts
   - This will find endpoints across different hosts that share common parameter names

## Running Custom Queries in Neo4j

You can run custom Cypher queries directly in Neo4j Browser:

1. Open Neo4j Desktop
2. Click "Open" next to your database
3. Click "Open Neo4j Browser"
4. Run your custom queries

### Example Queries

#### Basic Queries

```cypher
# List all hosts
MATCH (h:Host) RETURN h.name AS host

# List all endpoints
MATCH (h:Host)-[:HAS_ENDPOINT]->(e:Endpoint) 
RETURN h.name AS host, e.path AS path, e.method AS method LIMIT 100

# List all parameters with values
MATCH (e:Endpoint)-[:HAS_PARAMETER]->(p:Parameter) 
RETURN e.host AS host, e.path AS path, p.name AS param, p.values AS values LIMIT 100

# Find API endpoints
MATCH (e:Endpoint) WHERE e.path CONTAINS '/api/' 
RETURN e.host AS host, e.path AS path, e.method AS method

# Find all POST endpoints
MATCH (h:Host)-[:HAS_ENDPOINT]->(e:Endpoint) WHERE e.method = 'POST' 
RETURN h.name AS host, e.path AS path

# Find authentication/authorization parameters
MATCH (e:Endpoint)-[:HAS_PARAMETER]->(p:Parameter) 
WHERE p.name CONTAINS 'token' OR p.name CONTAINS 'key' OR p.name CONTAINS 'auth' 
RETURN e.host AS host, e.path AS path, p.name AS parameter, p.values AS values
```

#### Cross-Host Analysis

```cypher
# Find identical paths across different hosts
MATCH (h1:Host)-[:HAS_ENDPOINT]->(e1:Endpoint), (h2:Host)-[:HAS_ENDPOINT]->(e2:Endpoint) 
WHERE h1 <> h2 AND e1.path = e2.path 
RETURN h1.name AS host1, e1.path AS path1, h2.name AS host2, e2.path AS path2

# Find endpoints sharing parameter names across hosts
MATCH (e1:Endpoint)-[:HAS_PARAMETER]->(p:Parameter)<-[:HAS_PARAMETER]-(e2:Endpoint) 
WHERE e1.host <> e2.host 
RETURN e1.host AS host1, e1.path AS path1, e2.host AS host2, e2.path AS path2, p.name AS parameter

# Find session parameters shared across hosts
MATCH (e1:Endpoint)-[:HAS_PARAMETER]->(p1:Parameter), (e2:Endpoint)-[:HAS_PARAMETER]->(p2:Parameter) 
WHERE e1.host <> e2.host AND p1.name = p2.name AND p1.name CONTAINS 'session' 
RETURN e1.host AS host1, e1.path AS path1, p1.name AS param1, e2.host AS host2, e2.path AS path2, p2.name AS param2

# Find JWT parameters shared across hosts
MATCH (e1:Endpoint)-[:HAS_PARAMETER]->(p1:Parameter), (e2:Endpoint)-[:HAS_PARAMETER]->(p2:Parameter) 
WHERE e1.host <> e2.host AND p1.name = p2.name AND p1.name CONTAINS 'jwt' 
RETURN e1.host AS host1, e1.path AS path1, p1.name AS param1, e2.host AS host2, e2.path AS path2, p2.name AS param2

# Find matching API paths across hosts
MATCH (h1:Host)-[:HAS_ENDPOINT]->(e1:Endpoint), (h2:Host)-[:HAS_ENDPOINT]->(e2:Endpoint) 
WHERE h1 <> h2 AND e1.path CONTAINS '/api/' AND e2.path CONTAINS '/api/' AND split(e1.path, '/')[2] = split(e2.path, '/')[2] 
RETURN h1.name AS host1, e1.path AS path1, h2.name AS host2, e2.path AS path2, split(e1.path, '/')[2] AS apiVersion
```

#### Security Analysis

```cypher
# Find authentication parameters across hosts
MATCH (e1:Endpoint)-[:HAS_PARAMETER]->(p1:Parameter), (e2:Endpoint)-[:HAS_PARAMETER]->(p2:Parameter) 
WHERE e1.host <> e2.host AND p1.name = p2.name AND p1.name =~ '(?i).*auth.*|.*token.*|.*api[-_]?key.*|.*secret.*|.*password.*' 
RETURN e1.host AS host1, e1.path AS path1, p1.name AS param1, e2.host AS host2, e2.path AS path2, p2.name AS param2

# Find file inclusion parameters (potential LFI)
MATCH (e:Endpoint)-[:HAS_PARAMETER]->(p:Parameter) 
WHERE p.name =~ '(?i).*file.*|.*path.*|.*dir.*|.*include.*|.*require.*' 
RETURN e.host AS host, e.path AS path, e.method AS method, p.name AS param

# Find URL redirect parameters (potential open redirect)
MATCH (e:Endpoint)-[:HAS_PARAMETER]->(p:Parameter) 
WHERE p.name =~ '(?i).*redir.*|.*url.*|.*link.*|.*goto.*|.*next.*|.*target.*' 
RETURN e.host AS host, e.path AS path, e.method AS method, p.name AS param, p.values AS values

# Find search/query parameters (potential SQLi)
MATCH (e:Endpoint)-[:HAS_PARAMETER]->(p:Parameter) 
WHERE p.name =~ '(?i).*q.*|.*query.*|.*search.*|.*find.*' 
RETURN e.host AS host, e.path AS path, e.method AS method, p.name AS param
```

#### Host Relationship Analysis

```cypher
# Top host pairs by shared parameters
MATCH (h1:Host)-[:HAS_ENDPOINT]->(e1:Endpoint)-[:HAS_PARAMETER]->(p:Parameter)<-[:HAS_PARAMETER]-(e2:Endpoint)<-[:HAS_ENDPOINT]-(h2:Host) 
WHERE h1 <> h2 
WITH h1, h2, count(p) AS sharedParams 
RETURN h1.name AS host1, h2.name AS host2, sharedParams ORDER BY sharedParams DESC LIMIT 10

# Host statistics (endpoints and parameters)
MATCH (h:Host)-[:HAS_ENDPOINT]->(e:Endpoint)-[:HAS_PARAMETER]->(p:Parameter) 
WITH h, count(DISTINCT e) AS endpoints, count(DISTINCT p) AS params 
RETURN h.name AS host, endpoints, params ORDER BY endpoints DESC

# All connected host pairs (any relationship)
MATCH (h1:Host)-[:HAS_ENDPOINT]->(e1:Endpoint), (h2:Host)-[:HAS_ENDPOINT]->(e2:Endpoint) 
WHERE h1 <> h2 AND (e1.path = e2.path OR EXISTS((e1)-[:HAS_PARAMETER]->()<-[:HAS_PARAMETER]-(e2))) 
WITH DISTINCT h1, h2 
RETURN h1.name AS host1, h2.name AS host2
```

#### Graph Visualization Queries

For these queries, Neo4j Browser will display an interactive graph visualization:

```cypher
# Show all hosts as graph
MATCH (h:Host) RETURN h

# Show hosts and their endpoints
MATCH p=(h:Host)-[:HAS_ENDPOINT]->(e:Endpoint) RETURN p LIMIT 50

# Show host-endpoint-parameter chain
MATCH p=(h:Host)-[:HAS_ENDPOINT]->(e:Endpoint)-[:HAS_PARAMETER]->(param:Parameter) RETURN p LIMIT 50

# Show hosts connected by shared parameters
MATCH p=((h1:Host)-[:HAS_ENDPOINT]->()-[:HAS_PARAMETER]->()<-[:HAS_PARAMETER]-()<-[:HAS_ENDPOINT]-(h2:Host)) 
WHERE h1 <> h2 RETURN p LIMIT 20

# Show security-related parameters
MATCH p=((e:Endpoint)-[:HAS_PARAMETER]->(param:Parameter)) 
WHERE param.name =~ '(?i).*token.*|.*key.*|.*auth.*|.*session.*' RETURN p LIMIT 30
```

#### Multi-Host Relationship Queries

These queries focus exclusively on relationships between different hosts, showing only endpoints and parameters that are shared across multiple domains:

```cypher
# Find only parameters that exist on multiple hosts (shared parameters)
MATCH (p:Parameter)<-[:HAS_PARAMETER]-(e:Endpoint)
WITH p, count(DISTINCT e.host) AS hostCount
WHERE hostCount > 1
MATCH (e:Endpoint)-[:HAS_PARAMETER]->(p)
RETURN p.name AS parameter, collect(DISTINCT e.host) AS hosts, hostCount
ORDER BY hostCount DESC

# Find endpoint paths that appear on multiple hosts with the same method
MATCH (e:Endpoint)
WITH e.path AS path, e.method AS method, count(DISTINCT e.host) AS hostCount
WHERE hostCount > 1
MATCH (e:Endpoint)
WHERE e.path = path AND e.method = method
RETURN path, method, collect(DISTINCT e.host) AS hosts, hostCount
ORDER BY hostCount DESC

# Find hosts with the most shared endpoints with other hosts
MATCH (h1:Host)-[:HAS_ENDPOINT]->(e1:Endpoint), (h2:Host)-[:HAS_ENDPOINT]->(e2:Endpoint)
WHERE h1 <> h2 AND e1.path = e2.path AND e1.method = e2.method
WITH h1.name AS host, count(DISTINCT e1.path) AS sharedEndpoints, count(DISTINCT h2.name) AS connectedHosts
RETURN host, sharedEndpoints, connectedHosts
ORDER BY sharedEndpoints DESC

# Identify clusters of hosts based on shared parameters (hosts that share the most parameters)
MATCH (h1:Host)-[:HAS_ENDPOINT]->(:Endpoint)-[:HAS_PARAMETER]->(p:Parameter)<-[:HAS_PARAMETER]-(:Endpoint)<-[:HAS_ENDPOINT]-(h2:Host)
WHERE h1 <> h2
WITH h1.name AS host1, h2.name AS host2, count(DISTINCT p) AS sharedParams
WHERE sharedParams > 2
RETURN host1, host2, sharedParams
ORDER BY sharedParams DESC

# Find parameter patterns used across multiple hosts (only shared parameters)
MATCH (p:Parameter)<-[:HAS_PARAMETER]-(e:Endpoint)
WITH p.name AS paramName, count(DISTINCT e.host) AS hostCount
WHERE hostCount > 1
MATCH (e:Endpoint)-[:HAS_PARAMETER]->(p:Parameter)
WHERE p.name = paramName
RETURN paramName, collect(DISTINCT e.host) AS hosts, hostCount
ORDER BY hostCount DESC
```

#### Multi-Host Graph Visualization Queries

These queries will produce graph visualizations showing only shared components:

```cypher
# Show only endpoints that exist on multiple hosts
MATCH (e:Endpoint)
WITH e.path AS path, e.method AS method, count(DISTINCT e.host) AS hostCount
WHERE hostCount > 1
MATCH p=(h:Host)-[:HAS_ENDPOINT]->(e:Endpoint)
WHERE e.path = path AND e.method = method
RETURN p LIMIT 50

# Show only parameters that exist on multiple hosts
MATCH (p:Parameter)<-[:HAS_PARAMETER]-(e:Endpoint)
WITH p, count(DISTINCT e.host) AS hostCount
WHERE hostCount > 1
MATCH path=(h:Host)-[:HAS_ENDPOINT]->(e:Endpoint)-[:HAS_PARAMETER]->(p)
RETURN path LIMIT 50

# Show the interconnections between hosts via shared endpoints and parameters
MATCH (h1:Host)-[:HAS_ENDPOINT]->(e1:Endpoint), (h2:Host)-[:HAS_ENDPOINT]->(e2:Endpoint)
WHERE h1 <> h2 AND e1.path = e2.path
WITH h1, h2
MATCH p = shortestPath((h1)-[*..4]-(h2))
RETURN p LIMIT 30

# Show host clusters based on shared API endpoints
MATCH (h1:Host)-[:HAS_ENDPOINT]->(e1:Endpoint), (h2:Host)-[:HAS_ENDPOINT]->(e2:Endpoint)
WHERE h1 <> h2 AND e1.path = e2.path AND e1.path CONTAINS '/api/' AND e2.path CONTAINS '/api/'
WITH h1, h2
MATCH p = (h1)-[:HAS_ENDPOINT]->(e1:Endpoint), q = (h2)-[:HAS_ENDPOINT]->(e2:Endpoint)
WHERE e1.path = e2.path AND e1.path CONTAINS '/api/'
RETURN p, q LIMIT 40
```

#### Host Cluster Connection Queries

These queries specifically focus on showing only the connecting points between different host clusters, filtering out any nodes that don't bridge between hosts:

```cypher
# Show only the bridging parameters that connect different host clusters
MATCH (h1:Host)-[:HAS_ENDPOINT]->(e1:Endpoint)-[:HAS_PARAMETER]->(p:Parameter)<-[:HAS_PARAMETER]-(e2:Endpoint)<-[:HAS_ENDPOINT]-(h2:Host)
WHERE h1 <> h2
WITH p, collect(DISTINCT h1) AS hosts1, collect(DISTINCT h2) AS hosts2
WHERE size(hosts1) > 0 AND size(hosts2) > 0
MATCH (h:Host)-[:HAS_ENDPOINT]->(e:Endpoint)-[:HAS_PARAMETER]->(p)
RETURN p, collect(DISTINCT h) AS connected_hosts
LIMIT 50

# Show only the shared endpoints that form bridges between host clusters
MATCH (h1:Host)-[:HAS_ENDPOINT]->(e1:Endpoint), (h2:Host)-[:HAS_ENDPOINT]->(e2:Endpoint)
WHERE h1 <> h2 AND e1.path = e2.path AND e1.method = e2.method
WITH e1.path AS path, e1.method AS method
MATCH (h:Host)-[:HAS_ENDPOINT]->(e:Endpoint)
WHERE e.path = path AND e.method = method
RETURN e, collect(DISTINCT h) AS connected_hosts
LIMIT 50

# Create a host connection graph showing only the bridging nodes
MATCH (h1:Host)-[:HAS_ENDPOINT]->(e1:Endpoint)-[:HAS_PARAMETER]->(p:Parameter)<-[:HAS_PARAMETER]-(e2:Endpoint)<-[:HAS_ENDPOINT]-(h2:Host)
WHERE h1 <> h2
WITH DISTINCT h1, h2
MATCH (h1)-[r1:HAS_ENDPOINT]->(e1:Endpoint), (h2)-[r2:HAS_ENDPOINT]->(e2:Endpoint)
WHERE e1.path = e2.path
RETURN h1, h2, e1, e2, r1, r2
LIMIT 40

# Show host clusters connected by unique security parameters (only the connecting parameters)
MATCH (h1:Host)-[:HAS_ENDPOINT]->(e1:Endpoint)-[:HAS_PARAMETER]->(p:Parameter)<-[:HAS_PARAMETER]-(e2:Endpoint)<-[:HAS_ENDPOINT]-(h2:Host)
WHERE h1 <> h2 AND p.name =~ '(?i).*auth.*|.*token.*|.*key.*|.*session.*'
WITH p, collect(DISTINCT h1) + collect(DISTINCT h2) AS connected_hosts
MATCH path = (h:Host)-[:HAS_ENDPOINT]->(:Endpoint)-[:HAS_PARAMETER]->(p)
WHERE h IN connected_hosts
RETURN path
LIMIT 30

# Visualize host connections based on identical API endpoints
MATCH (h1:Host)-[:HAS_ENDPOINT]->(e1:Endpoint), (h2:Host)-[:HAS_ENDPOINT]->(e2:Endpoint)
WHERE h1 <> h2 AND e1.path = e2.path AND e1.path CONTAINS '/api/'
WITH DISTINCT e1.path AS apiPath
MATCH (h:Host)-[:HAS_ENDPOINT]->(e:Endpoint)
WHERE e.path = apiPath
WITH e, collect(DISTINCT h) AS hosts
WHERE size(hosts) > 1
MATCH path = (h:Host)-[:HAS_ENDPOINT]->(e)
WHERE h IN hosts
RETURN path
LIMIT 30
```

## Use Cases

This extension is particularly useful for:

1. **Security Assessment**: Find common patterns across different applications
2. **API Discovery**: Identify hidden or undocumented APIs
3. **Parameter Analysis**: Detect parameter patterns across hosts
4. **Vulnerability Identification**: Find similar functionality across different hosts that might share the same vulnerabilities

## Troubleshooting

- If the extension fails to load, ensure you're using Burp Suite Professional (not Community Edition)
- If you can't connect to Neo4j, check that the database is running and accessible
- For connection issues, verify your credentials and check Neo4j logs for errors

## Build from Source

```bash
git clone https://github.com/yourusername/burp-neo4j-analyzer.git
cd burp-neo4j-analyzer
./gradlew build
```

The JAR file will be in the `build/libs/` directory.
##  Screenshots

![image](https://github.com/user-attachments/assets/adb54b44-5cb0-45ea-bce9-77802a8c9745)

![image](https://github.com/user-attachments/assets/212afb08-188d-4cf9-949c-1d6de0af8413)



## License

This project is licensed under the MIT License - see the LICENSE file for details.
