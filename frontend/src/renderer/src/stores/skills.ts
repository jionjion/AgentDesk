import {defineStore} from 'pinia'
import {computed, ref} from 'vue'
import type {Skill, SkillFormData} from '@/types/skill'
import {
    deleteSkill as apiDeleteSkill,
    getSkills,
    setSkillEnabled as apiSetSkillEnabled,
    syncSkill as apiSyncSkill
} from '@/api/skills'

export const useSkillsStore = defineStore('skills', () => {
    // === State ===
    const skills = ref<Skill[]>([])
    const loading = ref(false)
    const searchQuery = ref('')
    const selectedCategory = ref<string | null>(null)

    // === Computed ===

    /** 所有分类 */
    const categories = computed(() => {
        const cats = new Set<string>()
        for (const s of skills.value) {
            if (s.category) cats.add(s.category)
        }
        return Array.from(cats).sort()
    })

    /** 按搜索词和分类过滤后的技能列表 */
    const filteredSkills = computed(() => {
        let list = skills.value
        const q = searchQuery.value.trim().toLowerCase()
        if (q) {
            list = list.filter(s =>
                s.name.toLowerCase().includes(q) ||
                s.id.toLowerCase().includes(q) ||
                s.description.toLowerCase().includes(q)
            )
        }
        if (selectedCategory.value) {
            list = list.filter(s => s.category === selectedCategory.value)
        }
        return list
    })

    /** 已启用的技能列表 */
    const enabledSkills = computed(() => skills.value.filter(s => s.enabled))

    /** 已启用技能的 ID 集合（快速查找） */
    const enabledSkillIds = computed(() =>
        new Set(enabledSkills.value.map(s => s.id))
    )

    /** 按分类分组 */
    const skillsByCategory = computed(() => {
        const map: Record<string, Skill[]> = {}
        for (const s of skills.value) {
            const cat = s.category || '未分类'
            if (!map[cat]) map[cat] = []
            map[cat].push(s)
        }
        return map
    })

    // === Actions ===

    /** 加载技能列表 */
    async function fetchSkills() {
        loading.value = true
        try {
            const res = await getSkills()
            skills.value = res.data
        } catch (e) {
            console.error('加载技能列表失败', e)
        } finally {
            loading.value = false
        }
    }

    /** 创建或更新技能 (sync) */
    async function saveSkill(data: SkillFormData) {
        const res = await apiSyncSkill(data)
        const idx = skills.value.findIndex(s => s.id === res.data.id)
        if (idx >= 0) {
            skills.value[idx] = res.data
        } else {
            skills.value.push(res.data)
        }
        return res.data
    }

    /** 删除技能 */
    async function deleteSkill(id: string) {
        await apiDeleteSkill(id)
        skills.value = skills.value.filter(s => s.id !== id)
    }

    /** 启用/禁用技能 */
    async function toggleSkillEnabled(skill: Skill) {
        const newEnabled = !skill.enabled
        await apiSetSkillEnabled(skill.id, newEnabled)
        const idx = skills.value.findIndex(s => s.id === skill.id)
        if (idx >= 0) {
            skills.value[idx] = {...skills.value[idx], enabled: newEnabled}
        }
    }

    return {
        // state
        skills,
        loading,
        searchQuery,
        selectedCategory,
        // computed
        categories,
        filteredSkills,
        enabledSkills,
        enabledSkillIds,
        skillsByCategory,
        // actions
        fetchSkills,
        saveSkill,
        deleteSkill,
        toggleSkillEnabled
    }
})
