package ng.itlearning.pay.assistant;

import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The MCP server (topic 15.3): Spring AI publishes every ToolCallbackProvider bean at POST /mcp (streamable HTTP).
 * Only the public tools: customer tools need a verified customer, which an MCP client can't give us yet.
 */
@Configuration
class McpConfig {

    @Bean
    ToolCallbackProvider mcpTools(PublicTools tools) {
        return MethodToolCallbackProvider.builder().toolObjects(tools).build();
    }
}
