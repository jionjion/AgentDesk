import {defineStore} from 'pinia'
import {computed, ref} from 'vue'
import type {MarketplaceSkill, Skill, SkillFormData} from '@/types/skill'
import {
    deleteSkill as apiDeleteSkill,
    getSkills,
    installMarketplaceSkill as apiInstallMarketplaceSkill,
    installSkillPackage as apiInstallPackage,
    searchMarketplaceSkills as apiSearchMarketplaceSkills,
    setSkillEnabled as apiSetSkillEnabled,
    syncSkill as apiSyncSkill
} from '@/api/skills'

export const useSkillsStore = defineStore('skills', () => {
    const skills = ref<Skill[]>([])
    const loading = ref(false)
    const localInstalling = ref(false)
    const searchQuery = ref('')

    const marketplaceSkills = ref<MarketplaceSkill[]>([])
    const marketplaceQuery = ref('')
    const marketplaceLoading = ref(false)
    const marketplaceError = ref('')
    const marketplacePage = ref(1)
    const marketplaceTotal = ref(0)
    const marketplaceInstallingId = ref<string | null>(null)
    const marketplacePageSize = 12

    const filteredSkills = computed(() => {
        const q = searchQuery.value.trim().toLowerCase()
        if (!q) return skills.value
        return skills.value.filter(skill =>
            skill.name.toLowerCase().includes(q) ||
            skill.id.toLowerCase().includes(q) ||
            skill.description.toLowerCase().includes(q)
        )
    })

    const enabledSkills = computed(() => skills.value.filter(skill => skill.enabled))
    const enabledSkillIds = computed(() => new Set(enabledSkills.value.map(skill => skill.id)))
    const hasMoreMarketplaceSkills = computed(() => marketplaceSkills.value.length < marketplaceTotal.value)
    const installing = computed(() => localInstalling.value || marketplaceInstallingId.value !== null)

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

    async function searchMarketplace(reset = true) {
        if (marketplaceLoading.value) return
        if (reset) marketplacePage.value = 1
        marketplaceLoading.value = true
        marketplaceError.value = ''
        try {
            const res = await apiSearchMarketplaceSkills(
                marketplaceQuery.value.trim(),
                marketplacePage.value,
                marketplacePageSize
            )
            if (reset) {
                marketplaceSkills.value = res.data.skills
            } else {
                const existing = new Set(marketplaceSkills.value.map(skill => skill.id))
                marketplaceSkills.value.push(...res.data.skills.filter(skill => !existing.has(skill.id)))
            }
            marketplaceTotal.value = res.data.total
            marketplacePage.value = res.data.pageNumber
        } catch (e: any) {
            marketplaceError.value = e?.response?.data?.message || e?.message || '技能社区暂时不可用'
        } finally {
            marketplaceLoading.value = false
        }
    }

    async function loadMoreMarketplace() {
        if (!hasMoreMarketplaceSkills.value || marketplaceLoading.value) return
        marketplacePage.value += 1
        await searchMarketplace(false)
    }

    async function installMarketplace(skill: MarketplaceSkill) {
        if (marketplaceInstallingId.value) return
        marketplaceInstallingId.value = skill.id
        marketplaceError.value = ''
        try {
            const res = await apiInstallMarketplaceSkill(skill.id)
            upsertSkill(res.data)
            const item = marketplaceSkills.value.find(candidate => candidate.id === skill.id)
            if (item) item.installed = true
            return res.data
        } catch (e: any) {
            marketplaceError.value = e?.response?.data?.message || e?.message || '技能安装失败'
            throw e
        } finally {
            marketplaceInstallingId.value = null
        }
    }

    /** 旧版 prompt 同步接口，仅保留兼容调用；技能中心不再提供创建入口。 */
    async function saveSkill(data: SkillFormData) {
        const res = await apiSyncSkill(data)
        upsertSkill(res.data)
        return res.data
    }

    async function installPackage(file: File) {
        localInstalling.value = true
        try {
            const res = await apiInstallPackage(file)
            upsertSkill(res.data)
            return res.data
        } finally {
            localInstalling.value = false
        }
    }

    async function deleteSkill(id: string) {
        const removed = skills.value.find(skill => skill.id === id)
        await apiDeleteSkill(id)
        skills.value = skills.value.filter(skill => skill.id !== id)
        if (removed?.sourceRef) {
            const marketSkill = marketplaceSkills.value.find(skill => skill.id === removed.sourceRef)
            if (marketSkill) marketSkill.installed = false
        }
    }

    async function toggleSkillEnabled(skill: Skill) {
        const newEnabled = !skill.enabled
        await apiSetSkillEnabled(skill.id, newEnabled)
        const idx = skills.value.findIndex(item => item.id === skill.id)
        if (idx >= 0) skills.value[idx] = {...skills.value[idx], enabled: newEnabled}
    }

    function upsertSkill(skill: Skill) {
        const idx = skills.value.findIndex(item => item.id === skill.id)
        if (idx >= 0) skills.value[idx] = skill
        else skills.value.push(skill)
    }

    return {
        skills,
        loading,
        installing,
        localInstalling,
        searchQuery,
        marketplaceSkills,
        marketplaceQuery,
        marketplaceLoading,
        marketplaceError,
        marketplaceTotal,
        marketplaceInstallingId,
        filteredSkills,
        enabledSkills,
        enabledSkillIds,
        hasMoreMarketplaceSkills,
        fetchSkills,
        searchMarketplace,
        loadMoreMarketplace,
        installMarketplace,
        saveSkill,
        installPackage,
        deleteSkill,
        toggleSkillEnabled
    }
})
