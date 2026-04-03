import { createRouter, createWebHashHistory } from 'vue-router'

const router = createRouter({
  history: createWebHashHistory(),
  routes: [
    {
      path: '/',
      redirect: '/chat'
    },
    {
      path: '/chat/:sessionId?',
      name: 'chat',
      component: () => import('@/views/ChatView.vue'),
      meta: { title: '聊天' }
    },
    {
      path: '/skills',
      name: 'skills',
      component: () => import('@/views/SkillsView.vue'),
      meta: { title: '技能' }
    },
    {
      path: '/scheduled-tasks',
      name: 'scheduled-tasks',
      component: () => import('@/views/ScheduledTasksView.vue'),
      meta: { title: '定时任务' }
    },
    {
      path: '/im-channel',
      name: 'im-channel',
      component: () => import('@/views/ImChannelView.vue'),
      meta: { title: 'IM 频道' }
    }
  ]
})

export default router
