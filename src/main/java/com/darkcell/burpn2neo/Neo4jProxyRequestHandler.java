package com.darkcell.burpn2neo;

import burp.api.montoya.MontoyaApi;
import burp.api.montoya.http.message.params.ParsedHttpParameter;
import burp.api.montoya.proxy.http.InterceptedRequest;
import burp.api.montoya.proxy.http.ProxyRequestHandler;
import burp.api.montoya.proxy.http.ProxyRequestReceivedAction;
import burp.api.montoya.proxy.http.ProxyRequestToBeSentAction;

import java.net.URL;
import java.util.List;

/**
 * Handles Burp proxy requests and logs them to Neo4j
 */
public class Neo4jProxyRequestHandler implements ProxyRequestHandler {
    private final Neo4jManager neo4jManager;
    private final MontoyaApi api;

    public Neo4jProxyRequestHandler(MontoyaApi api, Neo4jManager neo4jManager) {
        this.api = api;
        this.neo4jManager = neo4jManager;
    }

    @Override
    public ProxyRequestReceivedAction handleRequestReceived(InterceptedRequest interceptedRequest) {
        // Don't intercept the request, just let it continue
        return ProxyRequestReceivedAction.doNotIntercept(interceptedRequest);
    }

    @Override
    public ProxyRequestToBeSentAction handleRequestToBeSent(InterceptedRequest interceptedRequest) {
        // Only process if Neo4j is connected
        if (!neo4jManager.isConnected()) {
            return ProxyRequestToBeSentAction.continueWith(interceptedRequest);
        }

        try {
            // Extract information
            String hostName = interceptedRequest.httpService().host();
            
            // Extract URL path
            String urlString = interceptedRequest.url();
            String path;
            try {
                path = new URL(urlString).getPath();
                if (path.isEmpty()) {
                    path = "/";
                }
            } catch (Exception e) {
                path = urlString;
            }
            
            String method = interceptedRequest.method();
            
            // Add to Neo4j database
            neo4jManager.upsertHost(hostName);
            neo4jManager.upsertEndpoint(hostName, path, method);
            
            // Process parameters
            List<ParsedHttpParameter> params = interceptedRequest.parameters();
            for (ParsedHttpParameter param : params) {
                neo4jManager.upsertParameter(hostName, path, param.name(), param.value());
            }
        } catch (Exception e) {
            api.logging().logToError("Error processing request: " + e.getMessage());
        }
        
        // Always continue with the original request
        return ProxyRequestToBeSentAction.continueWith(interceptedRequest);
    }
} 