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
    systemPrompt: string      // 旧版 prompt 技能兼容字段
    maxIters: number          // 旧版 prompt 技能兼容字段
    tools: string[]           // 工具类名列表
    builtin: boolean          // 是否内置技能
    enabled: boolean          // 当前用户是否启用
    skillType: 'prompt' | 'package' // 技能类型
    installPath?: string      // 技能包安装路径 (仅 package 类型)
    source: 'builtin' | 'modelscope' | 'local' | 'legacy'
    sourceRef?: string
}

/** 社区技能 — 由后端代理 ModelScope OpenAPI。 */
export interface MarketplaceSkill {
    id: string
    displayName: string
    description: string
    developer: string
    owner: string
    license: string
    sourceUrl: string
    category: string
    tags: string[]
    logoUrl: string
    viewCount: number
    downloads: number
    lastModified: string
    installCommands: string[]
    installed: boolean
}

export interface MarketplaceSkillPage {
    skills: MarketplaceSkill[]
    total: number
    pageNumber: number
    pageSize: number
}

/** 旧版 prompt 技能兼容数据。新技能统一以 SKILL.md 能力包安装。 */
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
