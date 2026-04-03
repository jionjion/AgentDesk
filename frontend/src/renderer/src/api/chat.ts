import request from './request'

const BASE_URL = 'http://localhost:8080'

/**
 * 创建 SSE 流式聊天连接
 */
export function createChatStream(sessionId: string, message: string): EventSource {
  const url = `${BASE_URL}/api/chat/stream?sessionId=${encodeURIComponent(sessionId)}&message=${encodeURIComponent(message)}`
  return new EventSource(url)
}

/**
 * 中断正在执行的 Agent
 */
export function interruptChat(sessionId: string) {
  return request.post(`/api/chat/${sessionId}/interrupt`)
}
