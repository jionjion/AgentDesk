/** 模型元数据定义 */
export interface ModelDefinition {
  id: string
  displayName: string
  group: string
  inputModalities: string[]
  supportsReasoning: boolean
  maxContextWindow: number
  maxOutputTokens: number
  description: string
}
