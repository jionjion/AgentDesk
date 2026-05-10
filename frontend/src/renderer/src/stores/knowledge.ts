import {defineStore} from 'pinia'
import {ref} from 'vue'
import type {KnowledgeBase, KnowledgeDocument} from '@/types/knowledge'
import * as api from '@/api/knowledge'

export const useKnowledgeStore = defineStore('knowledge', () => {
    const bases = ref<KnowledgeBase[]>([])
    const documents = ref<KnowledgeDocument[]>([])
    const isLoading = ref(false)

    async function loadBases() {
        isLoading.value = true
        try {
            const res = await api.getKnowledgeBases()
            bases.value = res.data
        } catch (e) {
            console.error('加载知识库列表失败', e)
        } finally {
            isLoading.value = false
        }
    }

    async function createBase(name: string, description?: string) {
        const res = await api.createKnowledgeBase({name, description})
        bases.value.unshift(res.data)
        return res.data
    }

    async function deleteBase(id: number) {
        await api.deleteKnowledgeBase(id)
        bases.value = bases.value.filter(kb => kb.id !== id)
    }

    async function loadDocuments(kbId: number) {
        const res = await api.getDocuments(kbId)
        documents.value = res.data
    }

    async function uploadDoc(kbId: number, file: File) {
        const res = await api.uploadDocument(kbId, file)
        documents.value.unshift(res.data)
        return res.data
    }

    async function deleteDoc(kbId: number, docId: number) {
        await api.deleteDocument(kbId, docId)
        documents.value = documents.value.filter(d => d.id !== docId)
    }

    /** 轮询文档处理状态, 返回停止函数 */
    function pollDocumentStatus(kbId: number, docId: number, interval = 3000): () => void {
        const timer = setInterval(async () => {
            try {
                const res = await api.getDocuments(kbId)
                documents.value = res.data
                const doc = res.data.find(d => d.id === docId)
                if (doc && (doc.status === 'done' || doc.status === 'failed')) {
                    clearInterval(timer)
                    await loadBases()
                }
            } catch {
                clearInterval(timer)
            }
        }, interval)

        return () => clearInterval(timer)
    }

    return {
        bases, documents, isLoading,
        loadBases, createBase, deleteBase,
        loadDocuments, uploadDoc, deleteDoc, pollDocumentStatus
    }
})
