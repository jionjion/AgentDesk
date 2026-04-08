import { createRouter, createWebHashHistory } from 'vue-router'

const router = createRouter({
  history: createWebHashHistory(),
  routes: [
    {
      path: '/',
      redirect: '/chat'
    },
    {
      path: '/login',
      name: 'login',
      component: () => import('@/views/LoginView.vue'),
      meta: { title: '登录', layout: 'none' }
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
      path: '/settings',
      name: 'settings',
      component: () => import('@/views/SettingsView.vue'),
      meta: { title: '设置', layout: 'none' }
    }
  ]
})

// 白名单 (无需登录即可访问)
const whiteList = ['/login']

router.beforeEach((to, _from, next) => {
  const token = localStorage.getItem('auth_token')

  if (token) {
    // 已登录 → 访问 login 页面则跳转到 chat
    if (to.path === '/login') {
      next('/chat')
    } else {
      next()
    }
  } else {
    // 未登录 → 白名单放行，其他跳转 login
    if (whiteList.includes(to.path)) {
      next()
    } else {
      next('/login')
    }
  }
})

export default router
