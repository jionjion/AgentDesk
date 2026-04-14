import request from './request'
import type {ModelDefinition} from '@/types/model'

/** 获取可用模型列表（按分组） */
export function getGroupedModels() {
    return request.get<Record<string, ModelDefinition[]>>('/api/models/grouped')
}
