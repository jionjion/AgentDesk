import request from './request'
import type {MarketplaceSkill, MarketplaceSkillPage, Skill, SkillFormData} from '@/types/skill'

/** 列出所有技能（内置 + 用户安装，含启用状态） */
export function getSkills() {
    return request.get<Skill[]>('/api/skills')
}

/** 获取单个技能详情 */
export function getSkill(id: string) {
    return request.get<Skill>(`/api/skills/${encodeURIComponent(id)}`)
}

/** 从 ModelScope 社区检索技能。 */
export function searchMarketplaceSkills(query = '', page = 1, size = 12) {
    return request.get<MarketplaceSkillPage>('/api/skills/marketplace', {
        params: {query, page, size}
    })
}

/** 获取社区技能详情。 */
export function getMarketplaceSkill(skillId: string) {
    return request.get<MarketplaceSkill>('/api/skills/marketplace/detail', {
        params: {skillId}
    })
}

/** 从社区下载并安装技能。 */
export function installMarketplaceSkill(skillId: string) {
    return request.post<Skill>('/api/skills/marketplace/install', {skillId})
}

/** 同步/上传技能定义（upsert）— prompt 型 */
export function syncSkill(data: SkillFormData) {
    return request.post<Skill>('/api/skills/sync', data)
}

/** 上传并安装 ZIP 技能包 — package 型 */
export function installSkillPackage(file: File) {
    const formData = new FormData()
    formData.append('file', file)
    return request.post<Skill>('/api/skills/install', formData, {
        headers: {'Content-Type': 'multipart/form-data'}
    })
}

/** 获取技能包的资源文件列表 */
export function getSkillResources(id: string) {
    return request.get<string[]>(`/api/skills/${encodeURIComponent(id)}/resources`)
}

/** 读取技能包中某个资源文件的内容 */
export function readSkillResource(id: string, resourcePath: string) {
    return request.get<{ content: string }>(`/api/skills/${encodeURIComponent(id)}/resources/${resourcePath}`)
}

/** 启用/禁用技能 */
export function setSkillEnabled(id: string, enabled: boolean) {
    return request.put<{ message: string }>(`/api/skills/${encodeURIComponent(id)}/enabled`, {enabled})
}

/** 删除用户安装的技能 */
export function deleteSkill(id: string) {
    return request.delete(`/api/skills/${encodeURIComponent(id)}`)
}
