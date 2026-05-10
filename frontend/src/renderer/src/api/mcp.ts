import request from './request'
import type {McpServer, McpServerFormData, McpTestResult} from '@/types/mcp'

/** 获取 MCP 服务器列表 */
export function getMcpServers() {
    return request.get<McpServer[]>('/api/mcp-servers')
}

/** 创建 MCP 服务器 */
export function createMcpServer(data: McpServerFormData) {
    return request.post<McpServer>('/api/mcp-servers', data)
}

/** 更新 MCP 服务器 */
export function updateMcpServer(id: number, data: McpServerFormData) {
    return request.put<McpServer>(`/api/mcp-servers/${id}`, data)
}

/** 删除 MCP 服务器 */
export function deleteMcpServer(id: number) {
    return request.delete<{ message: string }>(`/api/mcp-servers/${id}`)
}

/** 切换启用/禁用 */
export function setMcpServerEnabled(id: number, enabled: boolean) {
    return request.put<{ message: string }>(`/api/mcp-servers/${id}/enabled`, {enabled})
}

/** 测试连接 */
export function testMcpServer(id: number) {
    return request.post<McpTestResult>(`/api/mcp-servers/${id}/test`, null, {timeout: 30000})
}
