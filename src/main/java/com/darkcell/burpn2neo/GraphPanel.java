package com.darkcell.burpn2neo;

import burp.api.montoya.MontoyaApi;
import org.neo4j.driver.Record;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.List;
import java.util.Vector;
import java.net.URI;

/**
 * Main UI panel for the Neo4j Graph Analyzer extension
 */
public class GraphPanel extends JPanel {
    private final MontoyaApi api;
    private final Neo4jManager neo4jManager;

    // Configuration panel components
    private JTextField uriField;
    private JTextField usernameField;
    private JPasswordField passwordField;
    private JButton connectButton;
    private JLabel connectionStatusLabel;

    // Import panel components
    private JButton importButton;
    private JProgressBar progressBar;
    private JTextArea logArea;

    // Analysis panel components
    private JComboBox<String> hostSelector;
    private JTable resultsTable;
    private DefaultTableModel tableModel;
    
    // Graph visualization panel
    private JPanel visualizationPlaceholder;

    public GraphPanel(MontoyaApi api, Neo4jManager neo4jManager) {
        this.api = api;
        this.neo4jManager = neo4jManager;

        setLayout(new BorderLayout());

        // Create tabbed pane
        JTabbedPane tabbedPane = new JTabbedPane();
        tabbedPane.addTab("Configuration", createConfigPanel());
        tabbedPane.addTab("Import", createImportPanel());
        tabbedPane.addTab("Analysis", createAnalysisPanel());
        tabbedPane.addTab("Visualization", createVisualizationPanel());

        add(tabbedPane, BorderLayout.CENTER);
    }

    /**
     * Creates the configuration panel
     */
    private JPanel createConfigPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(new EmptyBorder(10, 10, 10, 10));

        // Connection settings
        JPanel connectionPanel = new JPanel(new GridBagLayout());
        connectionPanel.setBorder(BorderFactory.createTitledBorder("Neo4j Connection"));
        
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(5, 5, 5, 5);

        // URI field
        gbc.gridx = 0;
        gbc.gridy = 0;
        connectionPanel.add(new JLabel("Neo4j URI:"), gbc);

        gbc.gridx = 1;
        gbc.weightx = 1.0;
        uriField = new JTextField("bolt://localhost:7687", 30);
        connectionPanel.add(uriField, gbc);

        // Username field
        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.weightx = 0;
        connectionPanel.add(new JLabel("Username:"), gbc);

        gbc.gridx = 1;
        gbc.weightx = 1.0;
        usernameField = new JTextField("neo4j", 20);
        connectionPanel.add(usernameField, gbc);

        // Password field
        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.weightx = 0;
        connectionPanel.add(new JLabel("Password:"), gbc);

        gbc.gridx = 1;
        gbc.weightx = 1.0;
        passwordField = new JPasswordField(20);
        connectionPanel.add(passwordField, gbc);

        // Connection button
        gbc.gridx = 0;
        gbc.gridy = 3;
        gbc.gridwidth = 2;
        gbc.weightx = 0;
        gbc.anchor = GridBagConstraints.CENTER;
        connectButton = new JButton("Connect to Neo4j");
        connectionPanel.add(connectButton, gbc);

        // Status label
        gbc.gridy = 4;
        connectionStatusLabel = new JLabel("Status: Not connected");
        connectionPanel.add(connectionStatusLabel, gbc);

        panel.add(connectionPanel, BorderLayout.NORTH);

        // Instructions
        JTextArea instructionsArea = new JTextArea(
            "Neo4j Graph Analyzer Setup Instructions:\n\n" +
            "1. Install Neo4j Desktop from https://neo4j.com/download/\n" +
            "2. Create a new database and start it\n" +
            "3. Set a password and note the connection details\n" +
            "4. Enter the connection details above and click Connect\n\n" +
            "Once connected, all proxy traffic will be captured and stored in the Neo4j database.\n" +
            "Use the Import tab to import existing proxy history.\n" +
            "Use the Analysis tab to explore relationships between hosts, endpoints, and parameters."
        );
        instructionsArea.setEditable(false);
        instructionsArea.setLineWrap(true);
        instructionsArea.setWrapStyleWord(true);
        instructionsArea.setBackground(panel.getBackground());
        instructionsArea.setBorder(new EmptyBorder(10, 10, 10, 10));
        
        panel.add(new JScrollPane(instructionsArea), BorderLayout.CENTER);

        // Add action listeners
        connectButton.addActionListener(e -> connectToNeo4j());

        return panel;
    }

    /**
     * Creates the import panel
     */
    private JPanel createImportPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(new EmptyBorder(10, 10, 10, 10));

        // Control panel
        JPanel controlPanel = new JPanel();
        importButton = new JButton("Import Proxy History");
        controlPanel.add(importButton);
        panel.add(controlPanel, BorderLayout.NORTH);

        // Progress panel
        JPanel progressPanel = new JPanel(new BorderLayout());
        progressBar = new JProgressBar(0, 100);
        progressBar.setStringPainted(true);
        progressPanel.add(progressBar, BorderLayout.NORTH);

        // Log area
        logArea = new JTextArea();
        logArea.setEditable(false);
        JScrollPane scrollPane = new JScrollPane(logArea);
        scrollPane.setPreferredSize(new Dimension(600, 300));
        progressPanel.add(scrollPane, BorderLayout.CENTER);

        panel.add(progressPanel, BorderLayout.CENTER);

        // Add action listeners
        importButton.addActionListener(e -> importProxyHistory());

        return panel;
    }

    /**
     * Creates the analysis panel
     */
    private JPanel createAnalysisPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(new EmptyBorder(10, 10, 10, 10));

        // Control panel
        JPanel controlPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        
        controlPanel.add(new JLabel("Select Host:"));
        hostSelector = new JComboBox<>();
        hostSelector.setPreferredSize(new Dimension(300, 25));
        controlPanel.add(hostSelector);
        
        JButton refreshButton = new JButton("Refresh Hosts");
        controlPanel.add(refreshButton);
        
        JButton analyzeButton = new JButton("Find Similar Endpoints");
        controlPanel.add(analyzeButton);
        
        panel.add(controlPanel, BorderLayout.NORTH);

        // Results table
        tableModel = new DefaultTableModel(
            new Object[][] {},
            new String[] {"Host 1", "Path 1", "Host 2", "Path 2", "Parameter"}
        );
        resultsTable = new JTable(tableModel);
        resultsTable.setAutoResizeMode(JTable.AUTO_RESIZE_ALL_COLUMNS);
        
        JScrollPane scrollPane = new JScrollPane(resultsTable);
        scrollPane.setPreferredSize(new Dimension(600, 400));
        panel.add(scrollPane, BorderLayout.CENTER);

        // Add action listeners
        refreshButton.addActionListener(e -> refreshHosts());
        analyzeButton.addActionListener(e -> findSimilarEndpoints());
        hostSelector.addActionListener(e -> hostSelected());

        return panel;
    }
    
    /**
     * Creates the graph visualization panel
     */
    private JPanel createVisualizationPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(new EmptyBorder(10, 10, 10, 10));
        
        // Initialize the visualization panel with proper sizing
        GraphVisualizationPanel visualizationPanel = new GraphVisualizationPanel(api, neo4jManager);
        
        // Set preferred size to ensure enough space for visualizations
        // but not so large that it breaks Burp's UI layout
        visualizationPanel.setPreferredSize(new Dimension(800, 600));
        
        // Add to a scroll pane to handle overflow
        JScrollPane scrollPane = new JScrollPane(visualizationPanel);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        
        // Apply a maximum size to prevent UI stretching
        scrollPane.setMaximumSize(new Dimension(Integer.MAX_VALUE, 700));
        
        panel.add(scrollPane, BorderLayout.CENTER);
        
        return panel;
    }
    
    /**
     * Shows an error message when visualization cannot be enabled
     * @param panel The panel to add the error message to
     * @param t The throwable that caused the error
     */
    private void showVisualizationErrorMessage(JPanel panel, Throwable t) {
        panel.removeAll();
        panel.setLayout(new BorderLayout());
        
        JPanel messagePanel = new JPanel();
        messagePanel.setLayout(new BoxLayout(messagePanel, BoxLayout.Y_AXIS));
        
        JLabel errorLabel = new JLabel("Graph visualization could not be enabled.");
        errorLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        errorLabel.setFont(errorLabel.getFont().deriveFont(Font.BOLD, 14));
        messagePanel.add(errorLabel);
        messagePanel.add(Box.createVerticalStrut(10));
        
        JLabel alternativeLabel = new JLabel("You can still view and analyze your data through the Neo4j Browser.");
        alternativeLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        messagePanel.add(alternativeLabel);
        messagePanel.add(Box.createVerticalStrut(10));
        
        JButton openBrowserButton = new JButton("Open Neo4j Browser");
        openBrowserButton.setAlignmentX(Component.LEFT_ALIGNMENT);
        openBrowserButton.addActionListener(e -> {
            try {
                Desktop.getDesktop().browse(new URI("http://localhost:7474"));
            } catch (Exception ex) {
                api.logging().logToError("Error opening Neo4j Browser: " + ex.getMessage());
            }
        });
        messagePanel.add(openBrowserButton);
        messagePanel.add(Box.createVerticalStrut(20));
        
        JLabel detailsLabel = new JLabel("Error details:");
        detailsLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        detailsLabel.setFont(detailsLabel.getFont().deriveFont(Font.BOLD));
        messagePanel.add(detailsLabel);
        messagePanel.add(Box.createVerticalStrut(5));
        
        JTextArea errorDetails = new JTextArea();
        errorDetails.setText(t.toString());
        errorDetails.setEditable(false);
        errorDetails.setLineWrap(true);
        errorDetails.setWrapStyleWord(true);
        errorDetails.setBackground(panel.getBackground());
        errorDetails.setAlignmentX(Component.LEFT_ALIGNMENT);
        
        JScrollPane scrollPane = new JScrollPane(errorDetails);
        scrollPane.setPreferredSize(new Dimension(400, 100));
        scrollPane.setAlignmentX(Component.LEFT_ALIGNMENT);
        messagePanel.add(scrollPane);
        
        panel.add(messagePanel, BorderLayout.NORTH);
        panel.revalidate();
        panel.repaint();
    }

    /**
     * Connects to the Neo4j database
     */
    private void connectToNeo4j() {
        String uri = uriField.getText();
        String username = usernameField.getText();
        String password = new String(passwordField.getPassword());

        connectButton.setEnabled(false);
        connectionStatusLabel.setText("Connecting...");

        SwingUtilities.invokeLater(() -> {
            boolean connected = neo4jManager.connect(uri, username, password);
            if (connected) {
                connectionStatusLabel.setText("Status: Connected to " + uri);
                refreshHosts();
            } else {
                connectionStatusLabel.setText("Status: Connection failed");
            }
            connectButton.setEnabled(true);
        });
    }

    /**
     * Imports proxy history into Neo4j
     */
    private void importProxyHistory() {
        if (!checkConnection()) return;

        importButton.setEnabled(false);
        logArea.setText("");
        log("Fetching proxy history...");

        new Thread(() -> {
            try {
                // Get the proxy history using the API
                // This handles different versions of the Burp API by using reflection
                List<?> history = api.proxy().history();
                int total = history.size();

                if (total == 0) {
                    log("No proxy history found.");
                    SwingUtilities.invokeLater(() -> importButton.setEnabled(true));
                    return;
                }

                log("Found " + total + " proxy history items. Starting import...");
                SwingUtilities.invokeLater(() -> progressBar.setValue(0));

                // Cast to List<Object> to use our generic importer
                @SuppressWarnings("unchecked")
                List<Object> historyObjects = (List<Object>)(List<?>)history;
                
                neo4jManager.importProxyHistory(historyObjects, (current, totalItems) -> {
                    SwingUtilities.invokeLater(() -> {
                        int percentage = (int) (((double) current / totalItems) * 100);
                        progressBar.setValue(percentage);
                        
                        if (current % 10 == 0 || current == totalItems) {
                            log("Processed " + current + "/" + totalItems + " items");
                        }
                    });
                });

                log("Import completed.");
                SwingUtilities.invokeLater(() -> {
                    importButton.setEnabled(true);
                    refreshHosts();
                });

            } catch (Exception e) {
                log("Error importing proxy history: " + e.getMessage());
                SwingUtilities.invokeLater(() -> importButton.setEnabled(true));
            }
        }).start();
    }

    /**
     * Refreshes the host selector with hosts from Neo4j
     */
    private void refreshHosts() {
        if (!checkConnection()) return;

        new Thread(() -> {
            try {
                List<Record> hosts = neo4jManager.getAllHosts();
                
                SwingUtilities.invokeLater(() -> {
                    hostSelector.removeAllItems();
                    hostSelector.addItem("-- All Hosts --");
                    
                    for (Record host : hosts) {
                        hostSelector.addItem(host.get("host").asString());
                    }
                });
            } catch (Exception e) {
                api.logging().logToError("Error refreshing hosts: " + e.getMessage());
            }
        }).start();
    }

    /**
     * Finds similar endpoints across different hosts
     */
    private void findSimilarEndpoints() {
        if (!checkConnection()) return;

        clearResultsTable();

        new Thread(() -> {
            try {
                List<Record> similarities = neo4jManager.findSimilarEndpoints();

                SwingUtilities.invokeLater(() -> {
                    for (Record record : similarities) {
                        Vector<String> row = new Vector<>();
                        row.add(record.get("host1").asString());
                        row.add(record.get("path1").asString());
                        row.add(record.get("host2").asString());
                        row.add(record.get("path2").asString());
                        row.add(record.get("parameter").asString());
                        tableModel.addRow(row);
                    }
                });
            } catch (Exception e) {
                api.logging().logToError("Error finding similar endpoints: " + e.getMessage());
            }
        }).start();
    }

    /**
     * Handles host selection
     */
    private void hostSelected() {
        // Can be implemented later to show endpoints for selected host
    }

    /**
     * Clears the results table
     */
    private void clearResultsTable() {
        tableModel.setRowCount(0);
    }

    /**
     * Checks if Neo4j is connected, shows warning if not
     */
    private boolean checkConnection() {
        if (!neo4jManager.isConnected()) {
            JOptionPane.showMessageDialog(
                this,
                "Please connect to Neo4j database first.",
                "Not Connected",
                JOptionPane.WARNING_MESSAGE
            );
            return false;
        }
        return true;
    }

    /**
     * Adds a message to the log area
     */
    private void log(String message) {
        SwingUtilities.invokeLater(() -> {
            logArea.append(message + "\n");
            logArea.setCaretPosition(logArea.getDocument().getLength());
        });
    }
}