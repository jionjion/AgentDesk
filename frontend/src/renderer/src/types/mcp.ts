export type McpTransportType = 'sse' | 'stdio'

export interface McpSseConfig {
    url: string
    headers?: Record<string, string>
}

export interface McpStdioConfig {
    command: string[]
    env?: Record<string, string>
    workingDirectory?: string
}

export interface McpServer {
    id: number
    name: string
    description?: string
    type: McpTransportType
    config: McpSseConfig | McpStdioConfig
    enabled: boolean
    createdAt: number
    updatedAt: number
}

export interface McpServerFormData {
    name: string
    description?: string
    type: McpTransportType
    config: McpSseConfig | McpStdioConfig
}

export interface McpTestResult {
    success: boolean
    message: string
    availableTools: string[]
    latencyMs: number
}
