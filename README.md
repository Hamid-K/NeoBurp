# Burp Suite Neo4j Graph Analyzer

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

### Useful Queries

```cypher
// Find all hosts
MATCH (h:Host) RETURN h

// Find all endpoints for a specific host
MATCH (h:Host {name: 'example.com'})-[:HAS_ENDPOINT]->(e:Endpoint)
RETURN e.path, e.method

// Find common parameters across hosts
MATCH (e1:Endpoint)-[:HAS_PARAMETER]->(p:Parameter)<-[:HAS_PARAMETER]-(e2:Endpoint)
WHERE e1.host <> e2.host
RETURN e1.host, e1.path, e2.host, e2.path, p.name
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
