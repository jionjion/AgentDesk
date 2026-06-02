/** 沙箱工具定义 */
export interface SandboxTool {
  /** 工具唯一标识 */
  id: string
  /** 显示名称（即 Python 函数名） */
  name: string
  /** 功能描述（会注入到 AI prompt 中） */
  description: string
  /** Python 函数签名，如 read_pdf(path: str) -> list[str] */
  signature: string
  /** Python 实现代码 */
  code: string
  /** 依赖的 Python 包 */
  dependencies: string[]
  /** 是否为内置工具（内置不可删除） */
  builtin: boolean
  /** 是否启用 */
  enabled: boolean
  /** 版本号 */
  version: string
  /** 创建时间 */
  createdAt: number
  /** 更新时间 */
  updatedAt: number
}

/** 创建自定义工具时的表单数据 */
export interface SandboxToolFormData {
  name: string
  description: string
  signature: string
  code: string
  dependencies: string[]
}

/** 发送给后端的沙箱工具元数据（后端据此组装工具描述 prompt） */
export interface SandboxToolMeta {
  signature: string
  description: string
}
