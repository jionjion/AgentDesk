import {computed, ref, type Ref, watch} from 'vue'
import type {Skill} from '@/types/skill'
import {useSkillsStore} from '@/stores/skills'

export function useSlashCommand(inputText: Ref<string>) {
    const skillsStore = useSkillsStore()
    const showMenu = ref(false)
    const selectedIndex = ref(0)

    /** 从输入中提取 / 后的查询文本 */
    const slashQuery = computed(() => {
        const text = inputText.value
        // 仅在输入以 / 开头时触发
        if (!text.startsWith('/')) return null
        // 如果包含空格，说明已输入完整命令，不再显示菜单
        const query = text.slice(1)
        if (query.includes(' ') || query.includes('\n')) return null
        return query.toLowerCase()
    })

    /** 匹配的技能列表（已启用排前面） */
    const filteredSkills = computed<Skill[]>(() => {
        if (slashQuery.value === null) return []
        const q = slashQuery.value
        let candidates = [...skillsStore.skills]
        if (q) {
            candidates = candidates.filter(s =>
                s.name.toLowerCase().includes(q) || s.id.toLowerCase().includes(q) || s.description.toLowerCase().includes(q)
            )
        }
        // 已启用的排前面
        candidates.sort((a, b) => (a.enabled === b.enabled ? 0 : a.enabled ? -1 : 1))
        return candidates.slice(0, 8)
    })

    // 监听查询变化，控制菜单显隐
    watch(slashQuery, (val) => {
        if (val !== null && filteredSkills.value.length > 0) {
            showMenu.value = true
            selectedIndex.value = 0
        } else {
            showMenu.value = false
        }
    })

    /** 选择技能 → 调用后端启用 */
    function selectSkill(skill: Skill) {
        skillsStore.toggleSkillEnabled(skill)
        inputText.value = ''
        showMenu.value = false
    }

    /** 处理键盘事件，返回 true 表示已消费 */
    function handleKeydown(e: KeyboardEvent): boolean {
        if (!showMenu.value) return false

        if (e.key === 'ArrowDown') {
            e.preventDefault()
            selectedIndex.value = (selectedIndex.value + 1) % filteredSkills.value.length
            return true
        }
        if (e.key === 'ArrowUp') {
            e.preventDefault()
            selectedIndex.value = (selectedIndex.value - 1 + filteredSkills.value.length) % filteredSkills.value.length
            return true
        }
        if (e.key === 'Enter' && !e.shiftKey) {
            e.preventDefault()
            const skill = filteredSkills.value[selectedIndex.value]
            if (skill) selectSkill(skill)
            return true
        }
        if (e.key === 'Escape') {
            e.preventDefault()
            showMenu.value = false
            return true
        }
        return false
    }

    return {
        showMenu,
        selectedIndex,
        filteredSkills,
        selectSkill,
        handleKeydown
    }
}
