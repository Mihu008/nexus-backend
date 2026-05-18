package com.nexus.agent.mcp;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springaicommunity.mcp.annotation.McpTool;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * MCP Server Implementation for Nexus AI.
 * Exposes tools for the agent to interact with the backend infrastructure.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NexusMcpServer {

    private final JdbcTemplate jdbcTemplate;

    /**
     * Tool for verifying the database connection and running diagnostic queries.
     * 
     * @param sql The SQL query to execute (restricted to SELECT for safety in this scaffold).
     * @return Result set or error message.
     */
    @McpTool(name = "query_database", description = "Executes a SELECT query on the Supabase database to verify connectivity and inspect data.")
    public String queryDatabase(String sql) {
        if (!sql.trim().toUpperCase().startsWith("SELECT")) {
            return "Error: Only SELECT queries are allowed for diagnostic verification.";
        }
        
        try {
            log.info("Executing MCP diagnostic query: {}", sql);
            List<Map<String, Object>> results = jdbcTemplate.queryForList(sql);
            return results.isEmpty() ? "Connection live. No records found." : results.toString();
        } catch (Exception e) {
            log.error("MCP Database tool failed: {}", e.getMessage());
            return "Connection Failed: " + e.getMessage();
        }
    }

    @McpTool(name = "check_db_health", description = "Checks the basic health of the Supabase connection.")
    public String checkHealth() {
        try {
            Integer result = jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            return (result != null && result == 1) ? "Database Status: ONLINE" : "Database Status: UNKNOWN";
        } catch (Exception e) {
            return "Database Status: OFFLINE (" + e.getMessage() + ")";
        }
    }
}
