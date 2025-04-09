package com.darkcell.burpn2neo;

import burp.api.montoya.MontoyaApi;
import burp.api.montoya.http.handler.*;
import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.http.message.responses.HttpResponse;

/**
 * Handles HTTP traffic for the Neo4j Graph Analyzer extension.
 * Processes requests and responses to extract data for Neo4j.
 */
public class BurpHttpHandler implements HttpHandler {
    private final MontoyaApi api;
    private final Neo4jManager neo4jManager;

    public BurpHttpHandler(MontoyaApi api, Neo4jManager neo4jManager) {
        this.api = api;
        this.neo4jManager = neo4jManager;
    }

    @Override
    public RequestToBeSentAction handleHttpRequestToBeSent(HttpRequestToBeSent requestToBeSent) {
        // We don't modify requests, just let them proceed
        return RequestToBeSentAction.continueWith(requestToBeSent);
    }

    @Override
    public ResponseReceivedAction handleHttpResponseReceived(HttpResponseReceived responseReceived) {
        try {
            // Only process in-scope items if configured to do so
            if (neo4jManager.isProcessInScopeOnly() && !api.scope().isInScope(responseReceived.initiatingRequest().url())) {
                return ResponseReceivedAction.continueWith(responseReceived);
            }

            // Create a request/response pair for processing
            HttpRequestResponse requestResponse = HttpRequestResponse.httpRequestResponse(
                    responseReceived.initiatingRequest(),
                    responseReceived
            );

            // Process the request/response in the Neo4j manager
            neo4jManager.processRequestResponse(requestResponse);
        } catch (Exception e) {
            api.logging().logToError("Error processing HTTP response: " + e.getMessage());
            e.printStackTrace();
        }

        // Continue with the original response
        return ResponseReceivedAction.continueWith(responseReceived);
    }
} 