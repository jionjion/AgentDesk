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
export type {ScheduledTask, ScheduledTaskFormData, ScheduledTaskLog} from './scheduledTask'
