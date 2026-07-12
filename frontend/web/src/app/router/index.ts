import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'

declare module 'vue-router' {
  interface RouteMeta {
    requiresAuth?: boolean
  }
}

const routes: RouteRecordRaw[] = [
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
      { path: 'remote', component: () => import('@/pages/conference/RemoteControlPage.vue') },
      { path: 'survey', component: () => import('@/pages/conference/SurveyPage.vue') },
      { path: 'ticket', component: () => import('@/pages/conference/TicketPage.vue') },
      { path: 'diagrams', component: () => import('@/pages/conference/DiagrammingPage.vue') },
      { path: 'notes', component: () => import('@/pages/conference/CollabNotesPage.vue') },
      { path: 'video', component: () => import('@/pages/conference/VideoConferencePage.vue') }
    ]
  },
  {
    path: '/dashboard',
    component: () => import('@/pages/dashboard/DashboardLayout.vue'),
    meta: { requiresAuth: true },
    children: [
      { path: '', component: () => import('@/pages/dashboard/DashboardHome.vue') },
      { path: 'conferences', component: () => import('@/pages/dashboard/ConferencesListPage.vue') },
      { path: 'conferences/new', component: () => import('@/pages/dashboard/NewConferencePage.vue') },
      { path: 'join', component: () => import('@/pages/dashboard/JoinConferencePage.vue') },
      { path: 'certificate-settings', component: () => import('@/pages/dashboard/CertificateSettingsPage.vue') },
      { path: 'admin/users', component: () => import('@/pages/dashboard/AdminUsersPage.vue') },
      { path: 'admin/event-types', component: () => import('@/pages/dashboard/EventTypesAdminPage.vue') },
      { path: 'admin/roles', component: () => import('@/pages/dashboard/RolesAdminPage.vue') },
      {
        path: 'conferences/:conferenceId/edit',
        component: () => import('@/pages/dashboard/EditConferencePage.vue'),
        props: true
      },
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
      },
      {
        path: 'conferences/:conferenceId/check-in',
        component: () => import('@/pages/dashboard/CheckInScannerPage.vue'),
        props: true
      },
      {
        path: 'conferences/:conferenceId/venue-map',
        component: () => import('@/pages/dashboard/VenueMapEditorPage.vue'),
        props: true
      }
    ]
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

const GUEST_ROUTES = ['/', '/login', '/register']

router.beforeEach((to) => {
  const token = localStorage.getItem('ib_token')

  // Already authenticated → skip landing/login/register
  if (token && GUEST_ROUTES.includes(to.path)) return '/dashboard'

  if (to.meta.requiresAuth && !token) return '/login'

  const roles = (localStorage.getItem('ib_role') || '').split(',').map((r) => r.trim())
  const isOrganizerOrAdmin = roles.includes('organizer') || roles.includes('admin')
  const organizerOnlyPaths = ['/dashboard/conferences', '/dashboard/conferences/new', '/dashboard/certificate-settings']
  if (organizerOnlyPaths.includes(to.path) && !isOrganizerOrAdmin) return '/dashboard'
  if (to.path === '/dashboard/admin/users' && !roles.includes('admin')) return '/dashboard'
  if (to.path === '/dashboard/admin/event-types' && !roles.includes('admin')) return '/dashboard'
  if (to.path === '/dashboard/admin/roles' && !roles.includes('admin')) return '/dashboard'
})

export default router
