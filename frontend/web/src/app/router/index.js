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
    path: '/register',
    component: () => import('@/pages/login/RegisterPage.vue')
  },
  {
    path: '/profile',
    component: () => import('@/pages/profile/ProfilePage.vue'),
    meta: { requiresAuth: true }
  },
  {
    path: '/c/:friendlyId',
    component: () => import('@/pages/conference/ConferencePage.vue'),
    children: [
      { path: '', redirect: to => `/c/${to.params.friendlyId}/doubts` },
      { path: 'doubts', component: () => import('@/pages/conference/CloudDoubtsPage.vue') },
      { path: 'topics', component: () => import('@/pages/conference/CloudTopicsPage.vue') },
      { path: 'words/:word', component: () => import('@/pages/conference/WordTimelinePage.vue') },
      { path: 'presentation', component: () => import('@/pages/conference/PresentationPage.vue') },
      { path: 'survey', component: () => import('@/pages/conference/SurveyPage.vue') }
    ]
  },
  {
    path: '/dashboard',
    component: () => import('@/pages/dashboard/DashboardLayout.vue'),
    meta: { requiresAuth: true },
    children: [
      { path: '', component: () => import('@/pages/dashboard/DashboardHome.vue') },
      { path: 'conferences/new', component: () => import('@/pages/dashboard/NewConferencePage.vue') },
      { path: 'join', component: () => import('@/pages/dashboard/JoinConferencePage.vue') },
      { path: 'certificate-settings', component: () => import('@/pages/dashboard/CertificateSettingsPage.vue') },
      { path: 'admin/users', component: () => import('@/pages/dashboard/AdminUsersPage.vue') },
      {
        path: 'conferences/:conferenceId/moderation/messages',
        component: () => import('@/pages/dashboard/ModerationMessagesPage.vue'),
        props: true
      },
      {
        path: 'conferences/:conferenceId/moderation/words',
        component: () => import('@/pages/dashboard/ModerationWordsPage.vue'),
        props: true
      },
      {
        path: 'conferences/:conferenceId/presentation',
        component: () => import('@/pages/dashboard/PresentationManagePage.vue'),
        props: true
      },
      {
        path: 'conferences/:conferenceId/speaker',
        component: () => import('@/pages/dashboard/SpeakerPanelPage.vue'),
        props: true
      },
      {
        path: 'conferences/:conferenceId/survey',
        component: () => import('@/pages/dashboard/SurveyManagePage.vue'),
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
  const organizerOnlyPaths = ['/dashboard/conferences/new', '/dashboard/certificate-settings']
  const roles = (localStorage.getItem('ib_role') || '').split(',').map((r) => r.trim())
  const isOrganizerOrAdmin = roles.includes('organizer') || roles.includes('admin')
  if (organizerOnlyPaths.includes(to.path) && !isOrganizerOrAdmin) {
    return '/dashboard'
  }
  if (to.path === '/dashboard/admin/users' && !roles.includes('admin')) {
    return '/dashboard'
  }
})

export default router
