/** 技能定义 — 对应后端 SkillResponseDto */
export interface Skill {
    id: string                // 技能唯一标识 (小写字母+数字+连字符)
    name: string              // 显示名称
    description: string       // 技能描述 (Agent 委派依据)
    author: string
    version: string
    category: string          // 分组 (writing, coding, data, file, other)
    tags: string[]
    icon?: string             // Lucide 图标名
    bgColor?: string
    systemPrompt: string      // 子代理系统提示词
    maxIters: number          // ReAct 最大迭代
    tools: string[]           // 工具类名列表
    builtin: boolean          // 是否内置技能
    enabled: boolean          // 当前用户是否启用
}

/** 创建/编辑技能的表单数据 — 对应后端 SkillDefinitionDto */
export interface SkillFormData {
    id: string
    name: string
    description: string
    systemPrompt: string
    icon?: string
    bgColor?: string
    category?: string
    tags?: string[]
    author?: string
    version?: string
    maxIters?: number
    tools?: string[]
}
