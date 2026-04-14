import request from './request'
import type {Skill, SkillFormData} from '@/types/skill'

/** 列出所有技能（内置 + 用户安装，含启用状态） */
export function getSkills() {
    return request.get<Skill[]>('/api/skills')
}

/** 获取单个技能详情 */
export function getSkill(id: string) {
    return request.get<Skill>(`/api/skills/${encodeURIComponent(id)}`)
}

/** 同步/上传技能定义（upsert） */
export function syncSkill(data: SkillFormData) {
    return request.post<Skill>('/api/skills/sync', data)
}

/** 启用/禁用技能 */
export function setSkillEnabled(id: string, enabled: boolean) {
    return request.put<{ message: string }>(`/api/skills/${encodeURIComponent(id)}/enabled`, {enabled})
}

/** 删除用户安装的技能 */
export function deleteSkill(id: string) {
    return request.delete(`/api/skills/${encodeURIComponent(id)}`)
}
