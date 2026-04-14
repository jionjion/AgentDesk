export interface User {
    id: string
    name: string
    avatar: string
    plan: string
}

export interface Task {
    id: string
    title: string
    createdAt: string
}

export interface Channel {
    id: string
    name: string
    type: string
}

export type {Skill, SkillFormData} from './skill'

export interface ScheduledTask {
    id: string
    title: string
    description: string
    cron: string
    enabled: boolean
}
