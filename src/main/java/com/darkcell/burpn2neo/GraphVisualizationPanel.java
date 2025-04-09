package com.darkcell.burpn2neo;

import burp.api.montoya.MontoyaApi;
import org.neo4j.driver.Record;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.HyperlinkEvent;
import javax.swing.event.HyperlinkListener;
import javax.swing.text.html.HTMLEditorKit;
import javax.swing.text.html.StyleSheet;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;
import java.io.StringWriter;
import java.io.PrintWriter;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;

/**
 * Interactive graph visualization panel using embedded HTML and Swing components
 * Provides both textual analysis and a simplified visualization preview
 */
public class GraphVisualizationPanel extends JPanel {
    // Simple JSON implementations to avoid external dependencies
    private static class JSONArray {
        private final List<Object> items = new ArrayList<>();
        
        public void put(Object value) {
            items.add(value);
        }
        
        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder("[");
            for (int i = 0; i < items.size(); i++) {
                if (i > 0) sb.append(",");
                Object item = items.get(i);
                if (item instanceof String) {
                    sb.append("\"").append(((String) item).replace("\"", "\\\"")).append("\"");
                } else {
                    sb.append(item);
                }
            }
            sb.append("]");
            return sb.toString();
        }
    }
    
    private static class JSONObject {
        private final Map<String, Object> map = new HashMap<>();
        
        public void put(String key, Object value) {
            map.put(key, value);
        }
        
        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder("{");
            boolean first = true;
            for (Map.Entry<String, Object> entry : map.entrySet()) {
                if (!first) sb.append(",");
                first = false;
                sb.append("\"").append(entry.getKey()).append("\":");
                Object value = entry.getValue();
                if (value instanceof String) {
                    sb.append("\"").append(((String) value).replace("\"", "\\\"")).append("\"");
                } else {
                    sb.append(value);
                }
            }
            sb.append("}");
            return sb.toString();
        }
    }

    private final MontoyaApi api;
    private final Neo4jManager neo4jManager;
    private JPanel mainPanel;
    private JEditorPane htmlPreview;
    private JTextArea queryArea;
    private JComboBox<String> exampleQueriesCombo;
    private JLabel statusLabel;

    private static final String[] EXAMPLE_QUERIES = {
        "MATCH (h:Host) RETURN h.name AS host",
        "MATCH (h:Host)-[:HAS_ENDPOINT]->(e:Endpoint) RETURN h.name AS host, e.path AS path, e.method AS method LIMIT 100",
        "MATCH (e:Endpoint)-[:HAS_PARAMETER]->(p:Parameter) RETURN e.host AS host, e.path AS path, p.name AS param, p.values AS values LIMIT 100",
        "MATCH (h1:Host)-[:HAS_ENDPOINT]->(e1:Endpoint), (h2:Host)-[:HAS_ENDPOINT]->(e2:Endpoint) WHERE h1 <> h2 AND e1.path = e2.path RETURN h1.name AS host1, e1.path AS path1, h2.name AS host2, e2.path AS path2",
        "MATCH p=(:Host)-[:HAS_ENDPOINT]->(:Endpoint)-[:HAS_PARAMETER]->(:Parameter) RETURN p LIMIT 25",
        "MATCH (e1:Endpoint)-[:HAS_PARAMETER]->(p:Parameter)<-[:HAS_PARAMETER]-(e2:Endpoint) WHERE e1.host <> e2.host RETURN e1.host AS host1, e1.path AS path1, e2.host AS host2, e2.path AS path2, p.name AS parameter",
        "MATCH (e:Endpoint) WHERE e.path CONTAINS '/api/' RETURN e.host AS host, e.path AS path, e.method AS method",
        "MATCH (h:Host)-[:HAS_ENDPOINT]->(e:Endpoint) WHERE e.method = 'POST' RETURN h.name AS host, e.path AS path",
        "MATCH (e:Endpoint)-[:HAS_PARAMETER]->(p:Parameter) WHERE p.name CONTAINS 'token' OR p.name CONTAINS 'key' OR p.name CONTAINS 'auth' RETURN e.host AS host, e.path AS path, p.name AS parameter, p.values AS values",
        "MATCH (e:Endpoint) WHERE e.path =~ '.*\\.(js|css|png|jpg|gif|ico)$' RETURN e.host AS host, e.path AS path, e.method AS method",
        // New cross-host analysis queries
        "MATCH (e1:Endpoint)-[:HAS_PARAMETER]->(p1:Parameter), (e2:Endpoint)-[:HAS_PARAMETER]->(p2:Parameter) WHERE e1.host <> e2.host AND p1.name = p2.name AND p1.name CONTAINS 'session' RETURN e1.host AS host1, e1.path AS path1, p1.name AS param1, e2.host AS host2, e2.path AS path2, p2.name AS param2",
        "MATCH (e1:Endpoint)-[:HAS_PARAMETER]->(p1:Parameter), (e2:Endpoint)-[:HAS_PARAMETER]->(p2:Parameter) WHERE e1.host <> e2.host AND p1.name = p2.name AND p1.name CONTAINS 'jwt' RETURN e1.host AS host1, e1.path AS path1, p1.name AS param1, e2.host AS host2, e2.path AS path2, p2.name AS param2",
        "MATCH (e1:Endpoint)-[:HAS_PARAMETER]->(p1:Parameter), (e2:Endpoint)-[:HAS_PARAMETER]->(p2:Parameter) WHERE e1.host <> e2.host AND p1.name = p2.name AND p1.name CONTAINS 'cookie' RETURN e1.host AS host1, e1.path AS path1, p1.name AS param1, e2.host AS host2, e2.path AS path2, p2.name AS param2",
        "MATCH (e1:Endpoint)-[:HAS_PARAMETER]->(p1:Parameter), (e2:Endpoint)-[:HAS_PARAMETER]->(p2:Parameter) WHERE e1.host <> e2.host AND p1.name = p2.name AND (p1.name CONTAINS 'id' OR p1.name CONTAINS 'user') RETURN e1.host AS host1, e1.path AS path1, p1.name AS param1, e2.host AS host2, e2.path AS path2, p2.name AS param2",
        "MATCH (h1:Host)-[:HAS_ENDPOINT]->(e1:Endpoint), (h2:Host)-[:HAS_ENDPOINT]->(e2:Endpoint) WHERE h1 <> h2 AND e1.path CONTAINS '/api/' AND e2.path CONTAINS '/api/' AND split(e1.path, '/')[2] = split(e2.path, '/')[2] RETURN h1.name AS host1, e1.path AS path1, h2.name AS host2, e2.path AS path2, split(e1.path, '/')[2] AS apiVersion",
        "MATCH (h1:Host)-[:HAS_ENDPOINT]->(e1:Endpoint), (h2:Host)-[:HAS_ENDPOINT]->(e2:Endpoint) WHERE h1 <> h2 AND e1.method = 'POST' AND e2.method = 'POST' AND e1.path = e2.path RETURN h1.name AS host1, e1.path AS path1, h2.name AS host2, e2.path AS path2, e1.method AS method",
        "MATCH (e1:Endpoint)-[:HAS_PARAMETER]->(p1:Parameter), (e2:Endpoint)-[:HAS_PARAMETER]->(p2:Parameter) WHERE e1.host <> e2.host AND p1.name = p2.name AND p1.name =~ '(?i).*auth.*|.*token.*|.*api[-_]?key.*|.*secret.*|.*password.*' RETURN e1.host AS host1, e1.path AS path1, p1.name AS param1, e2.host AS host2, e2.path AS path2, p2.name AS param2",
        "MATCH (e:Endpoint)-[:HAS_PARAMETER]->(p:Parameter) WHERE p.name =~ '(?i).*file.*|.*path.*|.*dir.*|.*include.*|.*require.*' RETURN e.host AS host, e.path AS path, e.method AS method, p.name AS param",
        "MATCH (e:Endpoint)-[:HAS_PARAMETER]->(p:Parameter) WHERE p.name =~ '(?i).*redir.*|.*url.*|.*link.*|.*goto.*|.*next.*|.*target.*' RETURN e.host AS host, e.path AS path, e.method AS method, p.name AS param, p.values AS values",
        "MATCH (e:Endpoint)-[:HAS_PARAMETER]->(p:Parameter) WHERE p.name =~ '(?i).*q.*|.*query.*|.*search.*|.*find.*' RETURN e.host AS host, e.path AS path, e.method AS method, p.name AS param",
        // Host cluster queries
        "MATCH (h1:Host)-[:HAS_ENDPOINT]->(e1:Endpoint)-[:HAS_PARAMETER]->(p:Parameter)<-[:HAS_PARAMETER]-(e2:Endpoint)<-[:HAS_ENDPOINT]-(h2:Host) WHERE h1 <> h2 WITH h1, h2, count(p) AS sharedParams RETURN h1.name AS host1, h2.name AS host2, sharedParams ORDER BY sharedParams DESC LIMIT 10",
        "MATCH (h1:Host), (h2:Host) WHERE h1 <> h2 MATCH (h1)-[:HAS_ENDPOINT]->(e1:Endpoint), (h2)-[:HAS_ENDPOINT]->(e2:Endpoint) WHERE e1.path = e2.path WITH h1, h2, count(e1) AS sharedEndpoints RETURN h1.name AS host1, h2.name AS host2, sharedEndpoints ORDER BY sharedEndpoints DESC LIMIT 10",
        "MATCH (h:Host)-[:HAS_ENDPOINT]->(e:Endpoint)-[:HAS_PARAMETER]->(p:Parameter) WITH h, count(DISTINCT e) AS endpoints, count(DISTINCT p) AS params RETURN h.name AS host, endpoints, params ORDER BY endpoints DESC",
        "MATCH (h1:Host)-[:HAS_ENDPOINT]->(e1:Endpoint), (h2:Host)-[:HAS_ENDPOINT]->(e2:Endpoint) WHERE h1 <> h2 AND (e1.path = e2.path OR EXISTS((e1)-[:HAS_PARAMETER]->()<-[:HAS_PARAMETER]-(e2))) WITH DISTINCT h1, h2 RETURN h1.name AS host1, h2.name AS host2",
        "MATCH (h1:Host)-[:HAS_ENDPOINT]->(:Endpoint)-[:HAS_PARAMETER]->(p:Parameter)<-[:HAS_PARAMETER]-(:Endpoint)<-[:HAS_ENDPOINT]-(h2:Host) WHERE h1 <> h2 AND p.name =~ '(?i).*token.*|.*key.*|.*auth.*|.*session.*|.*jwt.*' WITH DISTINCT h1, h2, p RETURN h1.name AS host1, h2.name AS host2, collect(DISTINCT p.name) AS sensitiveParams",
        // Graph visualization queries (return actual nodes and relationships)
        "MATCH (h:Host) RETURN h",
        "MATCH p=(h:Host)-[:HAS_ENDPOINT]->(e:Endpoint) RETURN p LIMIT 50",
        "MATCH p=(e:Endpoint)-[:HAS_PARAMETER]->(param:Parameter) RETURN p LIMIT 50",
        "MATCH p=(h:Host)-[:HAS_ENDPOINT]->(e:Endpoint)-[:HAS_PARAMETER]->(param:Parameter) RETURN p LIMIT 50",
        "MATCH p=((h1:Host)-[:HAS_ENDPOINT]->()-[:HAS_PARAMETER]->()<-[:HAS_PARAMETER]-()<-[:HAS_ENDPOINT]-(h2:Host)) WHERE h1 <> h2 RETURN p LIMIT 20",
        "MATCH (h1:Host), (h2:Host) WHERE h1 <> h2 MATCH p=((h1)-[:HAS_ENDPOINT]->(e1:Endpoint)), p2=((h2)-[:HAS_ENDPOINT]->(e2:Endpoint)) WHERE e1.path = e2.path RETURN p, p2 LIMIT 10",
        "MATCH p=((h:Host)-[:HAS_ENDPOINT]->(e:Endpoint)) WHERE e.method = 'POST' RETURN p LIMIT 50",
        "MATCH p=((e:Endpoint)-[:HAS_PARAMETER]->(param:Parameter)) WHERE param.name =~ '(?i).*token.*|.*key.*|.*auth.*|.*session.*' RETURN p LIMIT 30",
        "MATCH p=((e:Endpoint)-[:HAS_PARAMETER]->(param:Parameter)) WHERE param.name =~ '(?i).*file.*|.*path.*|.*dir.*|.*include.*' RETURN p LIMIT 30",
        "MATCH (h:Host) WITH h MATCH p=((h)-[:HAS_ENDPOINT]->(:Endpoint)-[:HAS_PARAMETER]->(:Parameter)) RETURN p LIMIT 50"
    };
    
    private static final String[] EXAMPLE_QUERY_DESCRIPTIONS = {
        "List all hosts",
        "List all endpoints",
        "List all parameters with values",
        "Find identical paths across different hosts",
        "Show host-endpoint-parameter relationships",
        "Find endpoints sharing parameter names across hosts",
        "Find API endpoints",
        "Find all POST endpoints",
        "Find authentication/authorization parameters",
        "Find static resource endpoints",
        // New cross-host analysis descriptions
        "Find session parameters shared across hosts",
        "Find JWT parameters shared across hosts",
        "Find cookie parameters shared across hosts",
        "Find ID/user parameters shared across hosts",
        "Find matching API paths across hosts",
        "Find matching POST endpoints across hosts",
        "Find authentication parameters across hosts",
        "Find file inclusion parameters (potential LFI)",
        "Find URL redirect parameters (potential open redirect)",
        "Find search/query parameters (potential SQLi)",
        // Host cluster descriptions
        "Top host pairs by shared parameters",
        "Top host pairs by shared endpoints",
        "Host statistics (endpoints and parameters)",
        "All connected host pairs (any relationship)",
        "Hosts sharing sensitive authentication parameters",
        // Graph visualization descriptions
        "[GRAPH] Show all hosts as graph",
        "[GRAPH] Show hosts and their endpoints",
        "[GRAPH] Show endpoints and their parameters",
        "[GRAPH] Show host-endpoint-parameter chain",
        "[GRAPH] Show hosts connected by shared parameters",
        "[GRAPH] Show hosts with identical endpoint paths",
        "[GRAPH] Show all POST endpoints as graph",
        "[GRAPH] Show security-related parameters",
        "[GRAPH] Show file-related parameters",
        "[GRAPH] Show complete graph for a host"
    };

    public GraphVisualizationPanel(MontoyaApi api, Neo4jManager neo4jManager) {
        this.api = api;
        this.neo4jManager = neo4jManager;
        
        setLayout(new BorderLayout());
        setBorder(BorderFactory.createEmptyBorder());
        initComponents();
    }

    private void initComponents() {
        mainPanel = new JPanel(new BorderLayout(10, 10));
        mainPanel.setBorder(new EmptyBorder(10, 10, 10, 10));
        
        // Create a header with info
        JPanel headerPanel = new JPanel(new BorderLayout());
        JLabel titleLabel = new JLabel("Graph Visualization");
        titleLabel.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 18));
        headerPanel.add(titleLabel, BorderLayout.WEST);
        
        // Create action buttons
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        
        JButton clearResultsButton = new JButton("Clear Results");
        clearResultsButton.addActionListener(e -> clearResults());
        buttonPanel.add(clearResultsButton);
        
        JButton openBrowserButton = new JButton("Open Neo4j Browser");
        openBrowserButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                try {
                    Desktop.getDesktop().browse(new java.net.URI("http://localhost:7474"));
                } catch (Exception ex) {
                    api.logging().logToError("Error opening Neo4j Browser: " + ex.getMessage());
                    JOptionPane.showMessageDialog(
                        GraphVisualizationPanel.this,
                        "Failed to open browser: " + ex.getMessage(),
                        "Error",
                        JOptionPane.ERROR_MESSAGE
                    );
                }
            }
        });
        buttonPanel.add(openBrowserButton);
        
        JButton copyQueryButton = new JButton("Copy Query");
        copyQueryButton.addActionListener(e -> {
            queryArea.selectAll();
            queryArea.copy();
            statusLabel.setText("Query copied to clipboard");
        });
        buttonPanel.add(copyQueryButton);
        
        JButton executeQueryButton = new JButton("Execute Query");
        executeQueryButton.addActionListener(e -> executeQuery());
        buttonPanel.add(executeQueryButton);
        
        headerPanel.add(buttonPanel, BorderLayout.EAST);
        mainPanel.add(headerPanel, BorderLayout.NORTH);
        
        // Main content panel with actions and visualization
        JPanel contentPanel = new JPanel(new BorderLayout());
        
        // Create analysis buttons panel (actions panel)
        JPanel actionsPanel = new JPanel(new BorderLayout());
        actionsPanel.setBorder(BorderFactory.createEmptyBorder(0, 0, 10, 0));
        
        // Top actions panel for standard operations
        JPanel topActionsPanel = new JPanel();
        topActionsPanel.setLayout(new FlowLayout(FlowLayout.LEFT));
        
        JButton showHostsButton = new JButton("Show All Hosts");
        showHostsButton.addActionListener(e -> loadAllHosts());
        topActionsPanel.add(showHostsButton);
        
        JButton findEndpointsButton = new JButton("Find Similar Endpoints");
        findEndpointsButton.addActionListener(e -> findSimilarEndpoints());
        topActionsPanel.add(findEndpointsButton);
        
        JButton findParamsButton = new JButton("Find Similar Parameters");
        findParamsButton.addActionListener(e -> findSimilarParameters());
        topActionsPanel.add(findParamsButton);
        
        JButton findAPIsButton = new JButton("Find Similar APIs");
        findAPIsButton.addActionListener(e -> findSimilarAPIs());
        topActionsPanel.add(findAPIsButton);
        
        // Add "Copy Example Queries" button to the top actions panel
        JButton showExampleQueriesButton = new JButton("Show Example Neo4j Queries");
        showExampleQueriesButton.addActionListener(e -> showCopyableQueries());
        topActionsPanel.add(showExampleQueriesButton);
        
        actionsPanel.add(topActionsPanel, BorderLayout.NORTH);
        
        // Bottom actions panel for query examples
        JPanel queryPanel = new JPanel(new BorderLayout());
        queryPanel.setBorder(new EmptyBorder(10, 0, 0, 0));
        
        JPanel queryControlsPanel = new JPanel(new BorderLayout());
        JLabel exampleLabel = new JLabel("Example Queries: ");
        queryControlsPanel.add(exampleLabel, BorderLayout.WEST);
        
        exampleQueriesCombo = new JComboBox<>(EXAMPLE_QUERY_DESCRIPTIONS);
        exampleQueriesCombo.addActionListener(e -> {
            int index = exampleQueriesCombo.getSelectedIndex();
            if (index >= 0 && index < EXAMPLE_QUERIES.length) {
                queryArea.setText(EXAMPLE_QUERIES[index]);
            }
        });
        queryControlsPanel.add(exampleQueriesCombo, BorderLayout.CENTER);
        
        // Add copy button for example query
        JButton copyExampleButton = new JButton("Copy");
        copyExampleButton.setToolTipText("Copy the selected example query to clipboard");
        copyExampleButton.addActionListener(e -> {
            int index = exampleQueriesCombo.getSelectedIndex();
            if (index >= 0 && index < EXAMPLE_QUERIES.length) {
                // Copy to clipboard
                StringSelection stringSelection = new StringSelection(EXAMPLE_QUERIES[index]);
                Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
                clipboard.setContents(stringSelection, null);
                statusLabel.setText("Example query copied to clipboard");
            }
        });
        queryControlsPanel.add(copyExampleButton, BorderLayout.EAST);
        
        queryPanel.add(queryControlsPanel, BorderLayout.NORTH);
        
        queryArea = new JTextArea(3, 60);
        queryArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        queryArea.setText(EXAMPLE_QUERIES[0]);
        JScrollPane queryScrollPane = new JScrollPane(queryArea);
        queryPanel.add(queryScrollPane, BorderLayout.CENTER);
        
        actionsPanel.add(queryPanel, BorderLayout.CENTER);
        
        contentPanel.add(actionsPanel, BorderLayout.NORTH);
        
        // Create visualization panel with HTML preview in a fixed-size container
        JPanel visualizationPanel = new JPanel(new BorderLayout());
        visualizationPanel.setBorder(new EmptyBorder(10, 0, 0, 0));
        
        htmlPreview = new JEditorPane();
        htmlPreview.setEditable(false);
        htmlPreview.setContentType("text/html");
        
        // Set up HTML styling
        HTMLEditorKit kit = new HTMLEditorKit();
        htmlPreview.setEditorKit(kit);
        StyleSheet styleSheet = kit.getStyleSheet();
        styleSheet.addRule("body { font-family: Arial, sans-serif; margin: 10px; padding: 0; }");
        styleSheet.addRule("h2 { color: #333366; margin-top: 10px; }");
        styleSheet.addRule("table { border-collapse: collapse; width: 100%; }");
        styleSheet.addRule("th { background-color: #f0f0f0; padding: 8px; text-align: left; border: 1px solid #ddd; }");
        styleSheet.addRule("td { padding: 8px; border: 1px solid #ddd; }");
        styleSheet.addRule(".node { background-color: #e1f5fe; padding: 5px; margin: 5px; border-radius: 5px; display: inline-block; }");
        styleSheet.addRule(".relationship { color: #666; padding: 0 5px; }");
        styleSheet.addRule(".host { background-color: #bbdefb; }");
        styleSheet.addRule(".endpoint { background-color: #c8e6c9; }");
        styleSheet.addRule(".parameter { background-color: #ffccbc; }");
        styleSheet.addRule(".graph-view { padding: 10px; background-color: #fafafa; border: 1px solid #ddd; }");
        
        // Add hyperlink support to open Neo4j Browser with specific queries
        htmlPreview.addHyperlinkListener(new HyperlinkListener() {
            @Override
            public void hyperlinkUpdate(HyperlinkEvent e) {
                if (e.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
                    try {
                        Desktop.getDesktop().browse(e.getURL().toURI());
                    } catch (Exception ex) {
                        api.logging().logToError("Error opening link: " + ex.getMessage());
                    }
                }
            }
        });
        
        // Create a scrollable pane with fixed height to prevent UI issues
        JScrollPane htmlScrollPane = new JScrollPane(htmlPreview);
        htmlScrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
        htmlScrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        
        // Set a fixed size for the preview area - key to preventing UI overflow
        Dimension previewSize = new Dimension(800, 350);
        htmlScrollPane.setPreferredSize(previewSize);
        htmlScrollPane.setMinimumSize(new Dimension(200, 200));
        htmlScrollPane.setMaximumSize(new Dimension(Integer.MAX_VALUE, 500)); 
        
        visualizationPanel.add(htmlScrollPane, BorderLayout.CENTER);
        
        contentPanel.add(visualizationPanel, BorderLayout.CENTER);
        
        mainPanel.add(contentPanel, BorderLayout.CENTER);
        
        // Add status label
        statusLabel = new JLabel("Ready");
        statusLabel.setBorder(new EmptyBorder(5, 5, 5, 5));
        
        // Add all components to main panel
        add(mainPanel, BorderLayout.CENTER);
        add(statusLabel, BorderLayout.SOUTH);
        
        // Show initial message
        showWelcomeMessage();
    }
    
    /**
     * Shows the welcome message
     */
    private void showWelcomeMessage() {
        updateHTMLPreview("<h2>Neo4j Graph Visualization</h2>" +
            "<p>Use the buttons above to query the Neo4j database or create custom Cypher queries.</p>" +
            "<p>For a full interactive experience, click \"Open Neo4j Browser\".</p>" +
            "<p>Try one of these actions to get started:</p>" +
            "<ul>" +
            "  <li>Click \"Show All Hosts\" to see hosts in the database</li>" +
            "  <li>Select an example query and click \"Execute Query\"</li>" +
            "  <li>Click \"Find Similar Endpoints\" to identify related endpoints</li>" +
            "</ul>");
    }
    
    /**
     * Clears the results area and resets the query
     */
    private void clearResults() {
        // Reset the HTML preview
        showWelcomeMessage();
        
        // Reset query to first example if empty
        if (queryArea.getText().trim().isEmpty() && EXAMPLE_QUERIES.length > 0) {
            exampleQueriesCombo.setSelectedIndex(0);
            queryArea.setText(EXAMPLE_QUERIES[0]);
        }
        
        // Update status
        statusLabel.setText("Results cleared");
    }
    
    /**
     * Executes the current query from the query area
     */
    private void executeQuery() {
        String query = queryArea.getText().trim();
        if (query.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Please enter a query", "Empty Query", JOptionPane.WARNING_MESSAGE);
            return;
        }
        
        if (!checkConnection()) return;
        
        statusLabel.setText("Executing query...");
        
        new Thread(() -> {
            try {
                List<Record> results = neo4jManager.executeQuery(query);
                updateResultsFromQuery(results, query);
                updateStatus("Query executed successfully. Returned " + results.size() + " records.");
            } catch (Exception e) {
                updateStatus("Error: " + e.getMessage());
                logError("Error executing query: " + e.getMessage(), e);
                SwingUtilities.invokeLater(() -> {
                    JOptionPane.showMessageDialog(
                        this, 
                        "Error executing query: " + e.getMessage(), 
                        "Query Error", 
                        JOptionPane.ERROR_MESSAGE
                    );
                });
            }
        }).start();
    }
    
    /**
     * Updates the HTML preview with query results
     */
    private void updateResultsFromQuery(List<Record> records, String query) {
        if (records.isEmpty()) {
            updateHTMLPreview("<h2>Query Results</h2><p>No records found.</p>");
            return;
        }
        
        // Build HTML content with better layout and scrolling
        StringBuilder html = new StringBuilder();
        html.append("<html><body style=\"margin: 0; padding: 0;\">");
        html.append("<div style=\"padding: 15px; max-width: 100%;\">");
        html.append("<h2 style=\"color: #333366; margin-bottom: 15px;\">Query Results</h2>");
        html.append("<div style=\"background-color: #f5f5f5; padding: 10px; border-left: 4px solid #2196F3; margin-bottom: 20px; font-family: monospace; white-space: pre-wrap; overflow-x: auto;\">");
        html.append(query);
        html.append("</div>");
        html.append("<p><strong>Records:</strong> ").append(records.size()).append("</p>");
        
        // Generate graph visualization first if appropriate query
        boolean hasGraphVisualization = 
            query.toLowerCase().contains("return p") || 
            query.toLowerCase().contains(":host") ||
            query.toLowerCase().contains(":endpoint") ||
            query.toLowerCase().contains("host") && query.toLowerCase().contains("path");

        if (hasGraphVisualization) {
            html.append("<div class=\"graph-section\" style=\"margin: 20px 0;\">");
            html.append("<h3 style=\"color: #333366; border-bottom: 1px solid #ddd; padding-bottom: 5px;\">Graph Visualization</h3>");
            html.append("<div class=\"graph-view\" style=\"padding: 20px; background-color: #f9f9f9; border: 1px solid #ddd; border-radius: 5px; margin-bottom: 20px;\">");
            
            if (records.size() > 10) {
                html.append("<p style=\"color: #666; font-style: italic; margin-bottom: 15px;\">Showing preview of first 10 results. For complete visualization, use Neo4j Browser.</p>");
            }
            
            // Add simple visualization
            for (int i = 0; i < Math.min(10, records.size()); i++) {
                Record record = records.get(i);
                addSimpleGraphPreview(html, record);
            }
            
            // Add "View in Neo4j Browser" button - ensuring it includes the query
            String encodedQuery = java.net.URLEncoder.encode(query, java.nio.charset.StandardCharsets.UTF_8);
            html.append("<div style=\"text-align: center; margin-top: 20px;\">");
            html.append("<a href=\"http://localhost:7474/browser/?cmd=").append(encodedQuery)
                .append("\" style=\"" +
                    "padding: 10px 20px; " +
                    "background-color: #4CAF50; " +
                    "color: white; " +
                    "text-decoration: none; " +
                    "border-radius: 4px; " +
                    "display: inline-block; " +
                    "font-weight: bold; " +
                    "box-shadow: 0 2px 4px rgba(0,0,0,0.1);\">" +
                    "<span style=\"margin-right: 5px;\">🔍</span> " +
                    "Open Interactive Graph in Neo4j Browser</a>");
            html.append("</div>");
            
            html.append("</div>");
            html.append("</div>");
        }
        
        // Generate table with results
        html.append("<div class=\"table-section\">");
        html.append("<h3 style=\"color: #333366; border-bottom: 1px solid #ddd; padding-bottom: 5px;\">Table Results</h3>");
        html.append("<div style=\"overflow-x: auto; max-width: 100%;\">");
        html.append("<table style=\"width: 100%; border-collapse: collapse; margin-top: 10px;\">");
        
        // Headers
        html.append("<tr style=\"background-color: #e1f5fe;\">");
        for (String key : records.get(0).keys()) {
            html.append("<th style=\"padding: 12px 8px; text-align: left; border: 1px solid #b3e5fc; font-weight: bold;\">").append(key).append("</th>");
        }
        html.append("</tr>");
        
        // Data rows - limit to 100 rows for UI performance
        int maxRows = Math.min(records.size(), 100);
        for (int i = 0; i < maxRows; i++) {
            Record record = records.get(i);
            String rowStyle = i % 2 == 0 ? "background-color: #ffffff;" : "background-color: #f5f5f5;";
            html.append("<tr style=\"").append(rowStyle).append("\">");
            
            for (String key : record.keys()) {
                String value = record.get(key).asObject() != null ? record.get(key).toString() : "null";
                // Handle long values
                if (value.length() > 100) {
                    value = value.substring(0, 97) + "...";
                }
                html.append("<td style=\"padding: 8px; border: 1px solid #ddd;\">").append(value).append("</td>");
            }
            html.append("</tr>");
        }
        
        // Add indicator if results were truncated
        if (records.size() > 100) {
            html.append("<tr><td colspan=\"").append(records.get(0).keys().size()).append("\" style=\"text-align: center; padding: 10px; background-color: #fff3cd; color: #856404;\">");
            html.append("Showing 100 of ").append(records.size()).append(" results. Use Neo4j Browser for complete results.");
            html.append("</td></tr>");
        }
        
        html.append("</table>");
        html.append("</div>");
        html.append("</div>");
        html.append("</div>");
        html.append("</body></html>");
        
        updateHTMLPreview(html.toString());
    }
    
    /**
     * Adds a simple graph visualization for path results
     */
    private void addSimpleGraphPreview(StringBuilder html, Record record) {
        // Check for paths in the record
        boolean hasPathObject = false;
        for (String key : record.keys()) {
            if (record.get(key).type().name().equals("PATH")) {
                hasPathObject = true;
                // Enhanced path visualization with more visual elements and layout
                html.append("<div style=\"margin-bottom: 20px; padding: 15px; background-color: #f8f8f8; border-radius: 5px; box-shadow: 0 1px 3px rgba(0,0,0,0.1);\">");
                
                // Extract path components if possible
                try {
                    // Just show a generic path visualization
                    html.append("<div style=\"display: flex; align-items: center; justify-content: center;\">");
                    html.append("<span class=\"node host\" style=\"padding: 10px; border-radius: 4px; margin-right: 5px; font-weight: bold;\">HOST</span>");
                    html.append("<span class=\"relationship\" style=\"margin: 0 8px; font-size: 20px;\">→</span>");
                    html.append("<span class=\"node endpoint\" style=\"padding: 10px; border-radius: 4px; margin-right: 5px; font-weight: bold;\">ENDPOINT</span>");
                    html.append("<span class=\"relationship\" style=\"margin: 0 8px; font-size: 20px;\">→</span>");
                    html.append("<span class=\"node parameter\" style=\"padding: 10px; border-radius: 4px; font-weight: bold;\">PARAMETER</span>");
                    html.append("</div>");
                } catch (Exception e) {
                    // Fallback in case of error
                    html.append("<div>Complex path object (see Neo4j Browser for interactive visualization)</div>");
                }
                
                html.append("</div>");
                break;
            }
        }
        
        if (hasPathObject) return;
        
        // Check for node patterns
        boolean hasNodes = false;
        for (String key : record.keys()) {
            if (record.get(key).type().name().equals("NODE")) {
                hasNodes = true;
                html.append("<div style=\"margin-bottom: 20px; padding: 15px; background-color: #f8f8f8; border-radius: 5px; box-shadow: 0 1px 3px rgba(0,0,0,0.1);\">");
                html.append("<div style=\"display: flex; align-items: center; justify-content: center;\">");
                
                // Extract node labels if possible
                try {
                    String nodeInfo = record.get(key).toString();
                    String label = nodeInfo.contains(":") ? 
                                   nodeInfo.substring(nodeInfo.indexOf(":") + 1, nodeInfo.indexOf("{")) : 
                                   "Node";
                    
                    String styleClass = "node";
                    if (label.contains("Host")) styleClass += " host";
                    else if (label.contains("Endpoint")) styleClass += " endpoint";
                    else if (label.contains("Parameter")) styleClass += " parameter";
                    
                    html.append("<span class=\"" + styleClass + "\" style=\"padding: 10px; border-radius: 4px; font-weight: bold;\">");
                    html.append(label);
                    html.append("</span>");
                } catch (Exception e) {
                    // Fallback
                    html.append("<span class=\"node\" style=\"padding: 10px; border-radius: 4px;\">Node</span>");
                }
                
                html.append("</div>");
                html.append("</div>");
                break;
            }
        }
        
        if (hasNodes) return;
        
        // Check for common patterns in field names
        boolean hasHost = false, hasEndpoint = false, hasParam = false;
        for (String key : record.keys()) {
            if (key.contains("host")) hasHost = true;
            if (key.contains("path") || key.contains("endpoint")) hasEndpoint = true;
            if (key.contains("param")) hasParam = true;
        }
        
        if (hasHost) {
            // Create a more visually engaging representation
            html.append("<div style=\"margin-bottom: 20px; padding: 15px; background-color: #f8f8f8; border-radius: 5px; box-shadow: 0 1px 3px rgba(0,0,0,0.1);\">");
            html.append("<div style=\"display: flex; align-items: center; flex-wrap: wrap;\">");
            
            String host = "", path = "", param = "", method = "";
            
            for (String key : record.keys()) {
                if (key.contains("host") && record.get(key).asObject() != null) {
                    host = record.get(key).asString();
                }
                if (key.contains("path") && record.get(key).asObject() != null) {
                    path = record.get(key).asString();
                }
                if (key.contains("method") && record.get(key).asObject() != null) {
                    method = record.get(key).asString();
                }
                if (key.contains("param") && record.get(key).asObject() != null) {
                    param = record.get(key).asString();
                }
            }
            
            // Host node with cleaner styling
            html.append("<span class=\"node host\" style=\"" + 
                    "padding: 10px; " +
                    "background-color: #bbdefb; " +
                    "border-radius: 5px; " +
                    "box-shadow: 0 1px 2px rgba(0,0,0,0.1); " +
                    "display: inline-block; " +
                    "margin-right: 8px; " +
                    "font-weight: bold; " +
                    "border: 1px solid #90caf9;\">" +
                    host +
                    "</span>");
            
            // Add endpoint if available
            if (hasEndpoint) {
                html.append("<span class=\"relationship\" style=\"margin: 0 10px; color: #666; font-weight: bold; font-size: 16px;\">→</span>");
                html.append("<span class=\"node endpoint\" style=\"" +
                        "padding: 10px; " +
                        "background-color: #c8e6c9; " +
                        "border-radius: 5px; " +
                        "box-shadow: 0 1px 2px rgba(0,0,0,0.1); " +
                        "display: inline-block; " +
                        "margin-right: 8px; " +
                        "border: 1px solid #a5d6a7;\">");
                
                if (!method.isEmpty()) {
                    html.append("<span style=\"font-weight: bold; color: #2e7d32; margin-right: 5px;\">")
                        .append(method)
                        .append("</span>");
                }
                
                html.append(path).append("</span>");
            }
            
            // Add parameter if available
            if (hasParam) {
                html.append("<span class=\"relationship\" style=\"margin: 0 10px; color: #666; font-weight: bold; font-size: 16px;\">→</span>");
                html.append("<span class=\"node parameter\" style=\"" +
                        "padding: 10px; " +
                        "background-color: #ffccbc; " +
                        "border-radius: 5px; " +
                        "box-shadow: 0 1px 2px rgba(0,0,0,0.1); " +
                        "display: inline-block; " +
                        "border: 1px solid #ffab91;\">" +
                        param +
                        "</span>");
            }
            
            html.append("</div>");
            html.append("</div>");
        }
    }
    
    /**
     * Loads all hosts from the database
     */
    public void loadAllHosts() {
        if (!checkConnection()) return;
        
        statusLabel.setText("Loading hosts...");
        
        new Thread(() -> {
            try {
                List<Record> hosts = neo4jManager.getAllHosts();
                
                StringBuilder html = new StringBuilder();
                html.append("<h2>Hosts in Database</h2>");
                html.append("<p>Found ").append(hosts.size()).append(" hosts</p>");
                
                if (!hosts.isEmpty()) {
                    // Add visual representation of hosts
                    html.append(createHostVisualization(hosts));
                    
                    // Add tabular data
                    html.append("<h3>Host Details</h3>");
                    html.append("<table>");
                    html.append("<tr><th>Host</th><th>Actions</th></tr>");
                    
                    for (Record record : hosts) {
                        String hostName = record.get("host").asString();
                        
                        html.append("<tr>");
                        html.append("<td>").append(hostName).append("</td>");
                        
                        // Link to show endpoints
                        String query = "MATCH (h:Host {name: '" + hostName + "'})-[:HAS_ENDPOINT]->(e:Endpoint) RETURN e.path AS path, e.method AS method ORDER BY path";
                        String encodedQuery = java.net.URLEncoder.encode(query, java.nio.charset.StandardCharsets.UTF_8);
                        html.append("<td><a href=\"http://localhost:7474/browser/?cmd=").append(encodedQuery).append("\">View Endpoints</a></td>");
                        
                        html.append("</tr>");
                    }
                    
                    html.append("</table>");
                    
                    // Add graph visualization hint
                    String graphQuery = "MATCH (h:Host) RETURN h";
                    String encodedGraphQuery = java.net.URLEncoder.encode(graphQuery, java.nio.charset.StandardCharsets.UTF_8);
                    html.append("<p><a href=\"http://localhost:7474/browser/?cmd=").append(encodedGraphQuery).append("\">View Host Graph in Neo4j Browser</a></p>");
                }
                
                updateHTMLPreview(html.toString());
                updateStatus("Loaded " + hosts.size() + " hosts");
            } catch (Exception e) {
                updateStatus("Error: " + e.getMessage());
                logError("Error loading hosts: " + e.getMessage(), e);
            }
        }).start();
    }
    
    /**
     * Creates a visual representation of hosts
     */
    private String createHostVisualization(List<Record> hosts) {
        if (hosts.isEmpty()) return "";
        
        StringBuilder html = new StringBuilder();
        html.append("<div style=\"padding: 15px; background-color: #f8f8f8; border: 1px solid #ddd; border-radius: 5px; margin-bottom: 20px;\">");
        html.append("<h3>Host Visualization</h3>");
        
        // Create a grid of host nodes
        html.append("<div style=\"display: flex; flex-wrap: wrap; gap: 10px; margin-bottom: 15px;\">");
        
        for (Record host : hosts) {
            String hostName = host.get("host").asString();
            // Create a node for each host with enhanced styling
            html.append("<div style=\"padding: 10px; background-color: #bbdefb; border-radius: 5px; " +
                    "border: 1px solid #90caf9; min-width: 100px; text-align: center; font-weight: bold;\">");
            html.append(hostName);
            html.append("</div>");
        }
        
        html.append("</div>");
        
        // Add Neo4j Browser link
        String query = "MATCH (h:Host) RETURN h";
        String encodedQuery = java.net.URLEncoder.encode(query, java.nio.charset.StandardCharsets.UTF_8);
        html.append("<p style=\"margin-top: 15px;\"><a href=\"http://localhost:7474/browser/?cmd=").append(encodedQuery)
            .append("\" style=\"padding: 8px 15px; background-color: #4CAF50; color: white; text-decoration: none; border-radius: 4px; display: inline-block;\">")
            .append("View Interactive Graph in Neo4j Browser</a></p>");
        
        html.append("</div>");
        return html.toString();
    }
    
    /**
     * Finds similar endpoints by path
     */
    public void findSimilarEndpoints() {
        if (!checkConnection()) return;
        
        statusLabel.setText("Finding similar endpoints...");
        
        new Thread(() -> {
            try {
                List<Record> similarities = neo4jManager.findSimilarEndpointsByPath();
                
                StringBuilder html = new StringBuilder();
                html.append("<h2>Similar Endpoints</h2>");
                html.append("<p>Found ").append(similarities.size()).append(" similar endpoints</p>");
                
                if (!similarities.isEmpty()) {
                    html.append("<table>");
                    html.append("<tr><th>Host 1</th><th>Path 1</th><th>Host 2</th><th>Path 2</th></tr>");
                    
                    for (Record record : similarities) {
                        String host1 = record.get("host1").asString();
                        String path1 = record.get("path1").asString();
                        String method1 = record.get("method1").asString();
                        String host2 = record.get("host2").asString();
                        String path2 = record.get("path2").asString();
                        String method2 = record.get("method2").asString();
                        
                        html.append("<tr>");
                        html.append("<td>").append(host1).append("</td>");
                        html.append("<td>").append(method1).append(" ").append(path1).append("</td>");
                        html.append("<td>").append(host2).append("</td>");
                        html.append("<td>").append(method2).append(" ").append(path2).append("</td>");
                        html.append("</tr>");
                    }
                    
                    html.append("</table>");
                    
                    // Add visualization link
                    String graphQuery = "MATCH (h1:Host)-[:HAS_ENDPOINT]->(e1:Endpoint), " +
                                      "(h2:Host)-[:HAS_ENDPOINT]->(e2:Endpoint) " +
                                      "WHERE h1 <> h2 AND e1.path = e2.path " +
                                      "RETURN h1, e1, h2, e2 LIMIT 10";
                    String encodedGraphQuery = java.net.URLEncoder.encode(graphQuery, java.nio.charset.StandardCharsets.UTF_8);
                    html.append("<p><a href=\"http://localhost:7474/browser/?cmd=").append(encodedGraphQuery).append("\">View Similar Endpoints Graph in Neo4j Browser</a></p>");
                }
                
                updateHTMLPreview(html.toString());
                updateStatus("Found " + similarities.size() + " similar endpoints");
            } catch (Exception e) {
                updateStatus("Error: " + e.getMessage());
                logError("Error finding similar endpoints: " + e.getMessage(), e);
            }
        }).start();
    }
    
    /**
     * Finds similar parameters
     */
    public void findSimilarParameters() {
        if (!checkConnection()) return;
        
        statusLabel.setText("Finding similar parameters...");
        
        new Thread(() -> {
            try {
                List<Record> similarities = neo4jManager.findSimilarParameters();
                
                StringBuilder html = new StringBuilder();
                html.append("<h2>Similar Parameters</h2>");
                html.append("<p>Found ").append(similarities.size()).append(" similar parameters</p>");
                
                if (!similarities.isEmpty()) {
                    html.append("<table>");
                    html.append("<tr><th>Parameter</th><th>Host 1</th><th>Path 1</th><th>Host 2</th><th>Path 2</th></tr>");
                    
                    for (Record record : similarities) {
                        String host1 = record.get("host1").asString();
                        String path1 = record.get("path1").asString();
                        String host2 = record.get("host2").asString();
                        String path2 = record.get("path2").asString();
                        String parameter = record.get("parameter").asString();
                        
                        html.append("<tr>");
                        html.append("<td>").append(parameter).append("</td>");
                        html.append("<td>").append(host1).append("</td>");
                        html.append("<td>").append(path1).append("</td>");
                        html.append("<td>").append(host2).append("</td>");
                        html.append("<td>").append(path2).append("</td>");
                        html.append("</tr>");
                    }
                    
                    html.append("</table>");
                    
                    // Add visualization link
                    String graphQuery = "MATCH (e1:Endpoint)-[:HAS_PARAMETER]->(p:Parameter)<-[:HAS_PARAMETER]-(e2:Endpoint) " +
                                      "WHERE e1.host <> e2.host " +
                                      "RETURN e1, p, e2 LIMIT 25";
                    String encodedGraphQuery = java.net.URLEncoder.encode(graphQuery, java.nio.charset.StandardCharsets.UTF_8);
                    html.append("<p><a href=\"http://localhost:7474/browser/?cmd=").append(encodedGraphQuery).append("\">View Parameter Relationships in Neo4j Browser</a></p>");
                }
                
                updateHTMLPreview(html.toString());
                updateStatus("Found " + similarities.size() + " similar parameters");
            } catch (Exception e) {
                updateStatus("Error: " + e.getMessage());
                logError("Error finding similar parameters: " + e.getMessage(), e);
            }
        }).start();
    }
    
    /**
     * Finds similar API patterns
     */
    public void findSimilarAPIs() {
        if (!checkConnection()) return;
        
        statusLabel.setText("Finding similar API patterns...");
        
        new Thread(() -> {
            try {
                List<Record> similarities = neo4jManager.findSimilarAPIPatterns();
                
                StringBuilder html = new StringBuilder();
                html.append("<h2>Similar API Patterns</h2>");
                html.append("<p>Found ").append(similarities.size()).append(" similar API patterns</p>");
                
                if (!similarities.isEmpty()) {
                    html.append("<table>");
                    html.append("<tr><th>Host 1</th><th>Path 1</th><th>Host 2</th><th>Path 2</th><th>Similarity</th></tr>");
                    
                    for (Record record : similarities) {
                        String host1 = record.get("host1").asString();
                        String path1 = record.get("path1").asString();
                        String method1 = record.get("method1").asString();
                        String host2 = record.get("host2").asString();
                        String path2 = record.get("path2").asString();
                        String method2 = record.get("method2").asString();
                        
                        // Show similarity score if available
                        String similarityInfo = "";
                        try {
                            if (record.get("similarity") != null) {
                                double similarity = record.get("similarity").asDouble();
                                similarityInfo = String.format("%.2f", similarity);
                            }
                        } catch (Exception e) {
                            // Ignore if similarity not available
                        }
                        
                        html.append("<tr>");
                        html.append("<td>").append(host1).append("</td>");
                        html.append("<td>").append(method1).append(" ").append(path1).append("</td>");
                        html.append("<td>").append(host2).append("</td>");
                        html.append("<td>").append(method2).append(" ").append(path2).append("</td>");
                        html.append("<td>").append(similarityInfo).append("</td>");
                        html.append("</tr>");
                    }
                    
                    html.append("</table>");
                    
                    // Add visualization link
                    String graphQuery = "MATCH (h1:Host)-[:HAS_ENDPOINT]->(e1:Endpoint), " +
                                      "(h2:Host)-[:HAS_ENDPOINT]->(e2:Endpoint) " +
                                      "WHERE h1 <> h2 " +
                                      "AND e1.path CONTAINS '/api/' AND e2.path CONTAINS '/api/' " +
                                      "AND e1.method = e2.method " +
                                      "RETURN h1, e1, h2, e2 LIMIT 10";
                    String encodedGraphQuery = java.net.URLEncoder.encode(graphQuery, java.nio.charset.StandardCharsets.UTF_8);
                    html.append("<p><a href=\"http://localhost:7474/browser/?cmd=").append(encodedGraphQuery).append("\">View API Patterns in Neo4j Browser</a></p>");
                }
                
                updateHTMLPreview(html.toString());
                updateStatus("Found " + similarities.size() + " similar API patterns");
            } catch (Exception e) {
                updateStatus("Error: " + e.getMessage());
                logError("Error finding similar API patterns: " + e.getMessage(), e);
            }
        }).start();
    }
    
    /**
     * Displays a dialog with example Neo4j queries that can be copied with a click
     */
    private void showCopyableQueries() {
        JDialog dialog = new JDialog();
        dialog.setTitle("Example Neo4j Queries");
        dialog.setModal(true);
        dialog.setLayout(new BorderLayout());
        
        JPanel contentPanel = new JPanel();
        contentPanel.setLayout(new BoxLayout(contentPanel, BoxLayout.Y_AXIS));
        contentPanel.setBorder(new EmptyBorder(15, 15, 15, 15));
        
        // Add header
        JLabel headerLabel = new JLabel("Neo4j Query Library");
        headerLabel.setFont(headerLabel.getFont().deriveFont(Font.BOLD, 16));
        headerLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        contentPanel.add(headerLabel);
        
        JLabel instructionsLabel = new JLabel("Click any query to copy it to the clipboard. Queries marked [GRAPH] will display visual graph results in Neo4j Browser.");
        instructionsLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        contentPanel.add(instructionsLabel);
        contentPanel.add(Box.createVerticalStrut(15));
        
        // Category labels
        String[] categories = {
            "Basic Queries", 
            "Cross-Host Analysis", 
            "Host Relationship Analysis",
            "Graph Visualization Queries"
        };
        
        int[] categorySizes = {10, 10, 5, 10}; // Number of queries in each category
        int startIndex = 0;
        
        // Feedback label for copy operations
        JLabel feedbackLabel = new JLabel(" ");
        feedbackLabel.setForeground(new Color(0, 128, 0));
        
        // Create tabbed pane for categories
        JTabbedPane tabbedPane = new JTabbedPane();
        
        // For each category, add queries
        for (int c = 0; c < categories.length; c++) {
            JPanel categoryPanel = new JPanel();
            categoryPanel.setLayout(new BoxLayout(categoryPanel, BoxLayout.Y_AXIS));
            categoryPanel.setBorder(new EmptyBorder(10, 5, 5, 5));
            
            int endIndex = startIndex + categorySizes[c];
            
            // Add queries for this category
            for (int i = startIndex; i < endIndex; i++) {
                if (i >= EXAMPLE_QUERIES.length) break;
                
                final String query = EXAMPLE_QUERIES[i];
                final String description = EXAMPLE_QUERY_DESCRIPTIONS[i];
                
                JPanel queryPanel = new JPanel(new BorderLayout());
                queryPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
                queryPanel.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createMatteBorder(0, 0, 1, 0, Color.LIGHT_GRAY),
                    BorderFactory.createEmptyBorder(5, 0, 5, 0)
                ));
                
                // Description label with special styling for graph queries
                JLabel descLabel;
                if (description.startsWith("[GRAPH]")) {
                    descLabel = new JLabel("<html><b>" + description + "</b> <span style='color:#4CAF50;'>(displays as graph)</span></html>");
                } else {
                    descLabel = new JLabel("<html><b>" + description + "</b></html>");
                }
                queryPanel.add(descLabel, BorderLayout.NORTH);
                
                // Query text area with syntax highlighting
                JTextArea queryArea = new JTextArea(query);
                queryArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
                queryArea.setLineWrap(true);
                queryArea.setWrapStyleWord(true);
                queryArea.setEditable(false);
                queryArea.setBackground(new Color(245, 245, 245));
                queryArea.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
                queryArea.setCursor(new Cursor(Cursor.HAND_CURSOR));
                
                // Add click listener to copy
                queryArea.addMouseListener(new java.awt.event.MouseAdapter() {
                    public void mouseClicked(java.awt.event.MouseEvent evt) {
                        queryArea.selectAll();
                        queryArea.copy();
                        feedbackLabel.setText("Copied: " + description);
                        
                        // Reset after 2 seconds
                        new Timer(2000, e -> feedbackLabel.setText(" ")).start();
                    }
                });
                
                // Create a tooltip
                queryArea.setToolTipText("Click to copy this query");
                
                JScrollPane scrollPane = new JScrollPane(queryArea);
                scrollPane.setPreferredSize(new Dimension(600, 80));
                queryPanel.add(scrollPane, BorderLayout.CENTER);
                
                // "Copy" button for clarity
                JButton copyButton = new JButton("Copy");
                copyButton.addActionListener(e -> {
                    queryArea.selectAll();
                    queryArea.copy();
                    feedbackLabel.setText("Copied: " + description);
                    
                    // Reset after 2 seconds
                    new Timer(2000, event -> feedbackLabel.setText(" ")).start();
                });
                
                // "Open in Neo4j" button for direct opening
                JButton openButton = new JButton("Open in Neo4j");
                openButton.addActionListener(e -> {
                    try {
                        String encodedQuery = java.net.URLEncoder.encode(query, java.nio.charset.StandardCharsets.UTF_8);
                        Desktop.getDesktop().browse(new java.net.URI("http://localhost:7474/browser/?cmd=" + encodedQuery));
                    } catch (Exception ex) {
                        JOptionPane.showMessageDialog(dialog, 
                            "Error opening Neo4j Browser: " + ex.getMessage(), 
                            "Error", 
                            JOptionPane.ERROR_MESSAGE);
                    }
                });
                
                JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
                buttonPanel.add(openButton);
                buttonPanel.add(copyButton);
                
                queryPanel.add(buttonPanel, BorderLayout.EAST);
                
                categoryPanel.add(queryPanel);
                categoryPanel.add(Box.createVerticalStrut(10));
            }
            
            // Add scroll pane for this category
            JScrollPane categoryScrollPane = new JScrollPane(categoryPanel);
            tabbedPane.addTab(categories[c], categoryScrollPane);
            
            startIndex = endIndex;
        }
        
        // Add to main panel
        contentPanel.add(tabbedPane);
        
        // Add "Open Neo4j Browser" button
        JButton openBrowserButton = new JButton("Open Neo4j Browser");
        openBrowserButton.addActionListener(e -> {
            try {
                Desktop.getDesktop().browse(new java.net.URI("http://localhost:7474"));
                dialog.dispose();
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(dialog, 
                    "Error opening browser: " + ex.getMessage(), 
                    "Error", 
                    JOptionPane.ERROR_MESSAGE);
            }
        });
        openBrowserButton.setAlignmentX(Component.LEFT_ALIGNMENT);
        
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        buttonPanel.add(openBrowserButton);
        buttonPanel.add(feedbackLabel);
        
        contentPanel.add(Box.createVerticalStrut(10));
        contentPanel.add(buttonPanel);
        
        JScrollPane scrollPane = new JScrollPane(contentPanel);
        scrollPane.setPreferredSize(new Dimension(800, 600));
        
        dialog.add(scrollPane, BorderLayout.CENTER);
        dialog.pack();
        dialog.setLocationRelativeTo(this);
        dialog.setVisible(true);
    }
    
    // Utility methods
    private void updateHTMLPreview(final String html) {
        SwingUtilities.invokeLater(() -> {
            htmlPreview.setText(html);
            htmlPreview.setCaretPosition(0);
        });
    }
    
    private void updateStatus(final String status) {
        SwingUtilities.invokeLater(() -> {
            statusLabel.setText(status);
        });
    }
    
    private void logError(String message, Exception e) {
        api.logging().logToError(message);
        StringWriter sw = new StringWriter();
        e.printStackTrace(new PrintWriter(sw));
        api.logging().logToError(sw.toString());
    }
    
    /**
     * Checks if Neo4j is connected, shows warning if not
     */
    private boolean checkConnection() {
        if (!neo4jManager.isConnected()) {
            SwingUtilities.invokeLater(() -> {
                JOptionPane.showMessageDialog(
                    this,
                    "Please connect to Neo4j database first.",
                    "Not Connected",
                    JOptionPane.WARNING_MESSAGE
                );
                statusLabel.setText("Not connected to Neo4j");
            });
            return false;
        }
        return true;
    }
} 