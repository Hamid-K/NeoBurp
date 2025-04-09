package com.darkcell.burpn2neo;

import burp.api.montoya.BurpExtension;
import burp.api.montoya.MontoyaApi;
import burp.api.montoya.http.handler.HttpHandler;
import burp.api.montoya.ui.UserInterface;
import burp.api.montoya.ui.editor.HttpRequestEditor;

import javax.swing.*;

public class BurpNeo4jExtension implements BurpExtension {
    private static MontoyaApi api;
    private static Neo4jManager neo4jManager;

    @Override
    public void initialize(MontoyaApi api) {
        BurpNeo4jExtension.api = api;
        
        // Set extension name
        api.extension().setName("Neo4j Graph Analyzer");
        
        // Initialize components
        neo4jManager = new Neo4jManager(api);
        
        // Initialize Neo4j Manager
        if (neo4jManager.initialize()) {
            api.logging().logToOutput("Neo4j Manager initialized successfully");
        } else {
            api.logging().logToError("Failed to initialize Neo4j Manager");
        }
        
        // Register UI
        SwingUtilities.invokeLater(() -> {
            try {
                registerUI();
            } catch (Exception e) {
                api.logging().logToError(e);
            }
        });
        
        // Register event handlers
        registerHandlers();
        
        api.logging().logToOutput("Neo4j Graph Analyzer extension loaded");
    }
    
    private void registerUI() {
        // Create the GraphPanel
        GraphPanel graphPanel = new GraphPanel(api, neo4jManager);
        
        // Register the panel with Burp's UI
        api.userInterface().registerSuiteTab("Neo4j Graph", graphPanel);
    }
    
    private void registerHandlers() {
        // Create and register an HttpHandler
        HttpHandler httpHandler = new BurpHttpHandler(api, neo4jManager);
        api.http().registerHttpHandler(httpHandler);
        
        api.logging().logToOutput("Registered HTTP handlers");
    }
    
    // Static accessor methods
    public static MontoyaApi getApi() {
        return api;
    }
    
    public static Neo4jManager getNeo4jManager() {
        return neo4jManager;
    }
} 