const MEMORY_CATEGORY_LABELS: Readonly<Record<string, string>> = {
  PROFILE: '个人资料',
  IDENTITY: '身份信息',
  PREFERENCE: '输出偏好',
  WORKFLOW: '工作习惯',
  TOOL_PREFERENCE: '工具偏好',
  PROJECT_FACT: '项目事实',
  DECISION: '已确认决策',
  GLOSSARY: '术语与简称',
  STAKEHOLDER: '协作对象',
  CONSTRAINT: '稳定约束',
  ONGOING_STATE: '进行中状态',
  SCHEDULE: '日程安排',
  EPISODE_SUMMARY: '工作片段摘要',
  OTHER: '其他'
}

export function memoryCategoryLabel(category: string): string {
  return MEMORY_CATEGORY_LABELS[category.trim().toUpperCase()] ?? category
}
