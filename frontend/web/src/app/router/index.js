import { createRouter, createWebHistory } from 'vue-router'

const routes = [
  {
    path: '/',
    component: () => import('@/pages/landing/LandingPage.vue')
  },
  {
    path: '/login',
    component: () => import('@/pages/login/LoginPage.vue')
  },
  {
    path: '/c/:friendlyId',
    component: () => import('@/pages/conference/ConferencePage.vue'),
    children: [
      { path: '', redirect: to => `/c/${to.params.friendlyId}/doubts` },
      { path: 'doubts', component: () => import('@/pages/conference/CloudDoubtsPage.vue') },
      { path: 'topics', component: () => import('@/pages/conference/CloudTopicsPage.vue') },
      { path: 'words/:word', component: () => import('@/pages/conference/WordTimelinePage.vue') }
    ]
  },
  {
    path: '/dashboard',
    component: () => import('@/pages/dashboard/DashboardLayout.vue'),
    meta: { requiresAuth: true },
    children: [
      { path: '', component: () => import('@/pages/dashboard/DashboardHome.vue') },
      { path: 'conferences/new', component: () => import('@/pages/dashboard/NewConferencePage.vue') },
      {
        path: 'conferences/:conferenceId/moderation/messages',
        component: () => import('@/pages/dashboard/ModerationMessagesPage.vue'),
        props: true
      },
      {
        path: 'conferences/:conferenceId/moderation/words',
        component: () => import('@/pages/dashboard/ModerationWordsPage.vue'),
        props: true
      }
    ]
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

router.beforeEach((to) => {
  if (to.meta.requiresAuth) {
    const token = localStorage.getItem('ib_token')
    if (!token) return '/login'
  }
})

export default router
