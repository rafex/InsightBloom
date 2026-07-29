import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import { useAuthStore } from '@/features/auth/authStore'
import { redirectExpiredRoute } from '@/features/auth/sessionGuard'

declare module 'vue-router' {
  interface RouteMeta {
    requiresAuth?: boolean
  }
}

const routes: RouteRecordRaw[] = [
  {
    path: '/',
    // La cartelera es la entrada pública principal de InsightBloom. La landing
    // queda disponible como componente histórico, pero ya no intercepta la raíz.
    component: () => import('@/pages/public/PublicEventsPage.vue')
  },
  { path: '/events', component: () => import('@/pages/public/PublicEventsPage.vue') },
  { path: '/events/:friendlyId/checkout', component: () => import('@/pages/public/PublicTicketCheckoutPage.vue') },
  { path: '/events/:friendlyId', component: () => import('@/pages/public/PublicEventDetailPage.vue') },
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
    // Fuera de ConferencePage.vue a proposito: esa pagina siempre monta AppHeader (position:
    // sticky, z-index:100, "Dashboard"/"Salir") y el mapa de intro del evento ANTES de su
    // router-view -- para el IDE queremos pantalla completa real, sin ese chrome tapando el
    // panel de ayuda ni el flash del mapa mientras carga.
    path: '/ide-session',
    component: () => import('@/pages/conference/IdeSessionPage.vue')
  },
  {
    // Base personalizada configurada en JaaS. La pantalla valida sesión + boleto y sólo
    // entonces entra a /c/:friendlyId/video.
    path: '/jitsi/:friendlyId',
    component: () => import('@/pages/conference/JitsiInvitePage.vue')
  },
  {
    path: '/c/:friendlyId',
    component: () => import('@/pages/conference/ConferencePage.vue'),
    children: [
      // Antes redirigia a /presentation -- desde el candado por herramienta (2026-07-27),
      // Presentacion puede estar bloqueada al llegar. Flyer es la unica pestana (junto a Mi
      // boleto) que nunca se bloquea, asi que es el destino seguro por defecto.
      { path: '', redirect: to => `/c/${to.params.friendlyId}/flyer` },
      { path: 'flyer', component: () => import('@/pages/conference/FlyerPage.vue') },
      { path: 'schedule', component: () => import('@/pages/conference/ConferenceSchedulePage.vue') },
      { path: 'doubts', component: () => import('@/pages/conference/CloudDoubtsPage.vue') },
      { path: 'topics', component: () => import('@/pages/conference/CloudTopicsPage.vue') },
      { path: 'words/:word', component: () => import('@/pages/conference/WordTimelinePage.vue') },
      { path: 'presentation', component: () => import('@/pages/conference/PresentationPage.vue') },
      { path: 'remote', component: () => import('@/pages/conference/RemoteControlPage.vue') },
      { path: 'survey', component: () => import('@/pages/conference/SurveyPage.vue') },
      { path: 'ticket', component: () => import('@/pages/conference/TicketPage.vue') },
      { path: 'ide', component: () => import('@/pages/conference/IdePage.vue') },
      { path: 'diagrams', component: () => import('@/pages/conference/DiagrammingPage.vue') },
      { path: 'notes', component: () => import('@/pages/conference/CollabNotesPage.vue') },
      { path: 'video', component: () => import('@/pages/conference/VideoConferencePage.vue') },
      { path: 'whiteboard', component: () => import('@/pages/conference/WhiteboardPage.vue') }
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
      { path: 'admin/users/:uuid', component: () => import('@/pages/dashboard/UserDetailPage.vue') },
      { path: 'admin/event-types', component: () => import('@/pages/dashboard/EventTypesAdminPage.vue') },
      { path: 'admin/roles', component: () => import('@/pages/dashboard/RolesAdminPage.vue') },
      { path: 'admin/ai/:capability?', component: () => import('@/pages/dashboard/AdminAiSettingsPage.vue') },
      { path: 'admin/chat', redirect: '/dashboard/admin/ai' },
      { path: 'admin/device-access', component: () => import('@/pages/dashboard/AdminDeviceAccessPage.vue') },
      { path: 'admin/egress-policy', component: () => import('@/pages/dashboard/AdminEgressPolicyPage.vue') },
      {
        path: 'conferences/:conferenceId/edit',
        component: () => import('@/pages/dashboard/EditConferencePage.vue'),
        props: true
      },
      {
        path: 'conferences/:conferenceId/config',
        component: () => import('@/pages/dashboard/ConferenceConfigPage.vue'),
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
        path: 'conferences/:conferenceId/moderation/ide',
        component: () => import('@/pages/dashboard/ModerationIdePage.vue'),
        props: true
      },
      {
        path: 'conferences/:conferenceId/moderation/tools',
        component: () => import('@/pages/dashboard/ModerationToolsPage.vue'),
        props: true
      },
      {
        path: 'conferences/:conferenceId/device-blocks',
        component: () => import('@/pages/dashboard/DeviceBlocksPage.vue'),
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
        path: 'conferences/:conferenceId/certificate',
        component: () => import('@/pages/dashboard/CertificateEditorPage.vue'),
        props: true
      },
      {
        path: 'conferences/:conferenceId/certificate-legacy',
        component: () => import('@/pages/dashboard/CertificateSettingsPage.vue'),
        props: true
      },
      {
        path: 'conferences/:conferenceId/check-in',
        component: () => import('@/pages/dashboard/CheckInScannerPage.vue'),
        props: true
      },
      {
        path: 'conferences/:conferenceId/tickets',
        component: () => import('@/pages/dashboard/TicketManagementPage.vue'),
        props: true
      },
      {
        path: 'conferences/:conferenceId/venue-map',
        component: () => import('@/pages/dashboard/VenueMapEditorPage.vue'),
        props: true
      }
    ]
  },
  {
    // Catch-all al final: sin esto, una URL erronea renderizaba una pagina en blanco
    // (auditoria UX 2026-07-26).
    path: '/:pathMatch(.*)*',
    component: () => import('@/pages/public/NotFoundPage.vue')
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

const AUTH_ENTRY_ROUTES = ['/login', '/register']

router.beforeEach(async (to) => {
  // A dashboard tab opened from the public presentation receives its token
  // through the same-origin opener bridge. Wait before enforcing requiresAuth.
  await useAuthStore().waitForSessionBridge()
  const token = sessionStorage.getItem('ib_token')

  const expiresAt = localStorage.getItem('ib_expires_at')
  if (token && expiresAt && Number.isFinite(Date.parse(expiresAt)) && Date.parse(expiresAt) <= Date.now()) {
    const loginRedirect = redirectExpiredRoute()
    if (to.path !== '/login') return loginRedirect || '/login'
  }

  // La cartelera es pública también para usuarios autenticados. Solo evitamos
  // que una sesión activa vuelva a las pantallas de login/registro.
  if (token && AUTH_ENTRY_ROUTES.includes(to.path)) return '/dashboard'

  if (to.meta.requiresAuth && !token) return '/login'

  const roles = (localStorage.getItem('ib_role') || '').split(',').map((r) => r.trim())
  const isOrganizerOrAdmin = roles.includes('organizer') || roles.includes('admin')
  const organizerOnlyPaths = ['/dashboard/conferences/new', '/dashboard/certificate-settings']
  if (organizerOnlyPaths.includes(to.path) && !isOrganizerOrAdmin) return '/dashboard'
  if (to.path === '/dashboard/admin/users' && !roles.includes('admin')) return '/dashboard'
  if (to.path === '/dashboard/admin/event-types' && !roles.includes('admin')) return '/dashboard'
  if (to.path === '/dashboard/admin/roles' && !roles.includes('admin')) return '/dashboard'
  if ((to.path === '/dashboard/admin/chat' || to.path.startsWith('/dashboard/admin/ai')) && !roles.includes('admin')) return '/dashboard'
  if (to.path === '/dashboard/admin/device-access' && !roles.includes('admin')) return '/dashboard'
  if (to.path === '/dashboard/admin/egress-policy' && !roles.includes('admin')) return '/dashboard'
})

export default router
