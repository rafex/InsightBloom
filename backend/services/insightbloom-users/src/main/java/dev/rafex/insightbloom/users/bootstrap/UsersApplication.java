package dev.rafex.insightbloom.users.bootstrap;

import dev.rafex.ether.http.jetty12.routing.JettyRouteRegistry;
import dev.rafex.ether.http.jetty12.JettyServerConfig;
import dev.rafex.ether.http.jetty12.JettyServerFactory;
import dev.rafex.ether.json.JacksonJsonCodec;
import dev.rafex.insightbloom.common.http.VersionHandler;
import dev.rafex.insightbloom.users.adapters.inbound.http.handlers.*;
import dev.rafex.insightbloom.users.adapters.outbound.cascade.HttpCascadeDeleteClient;
import dev.rafex.insightbloom.users.adapters.outbound.sqlite.*;
import dev.rafex.insightbloom.users.adapters.outbound.surveyclient.HttpSurveyClient;
import dev.rafex.insightbloom.users.adapters.outbound.telegramclient.HttpTelegramNotifyClient;
import dev.rafex.insightbloom.users.adapters.outbound.twilio.TwilioSmsClient;
import dev.rafex.insightbloom.users.adapters.outbound.zoho.ZohoEmailClient;
import dev.rafex.insightbloom.users.adapters.outbound.llm.EmailLlmClient;
import dev.rafex.insightbloom.users.application.usecases.*;
import dev.rafex.insightbloom.users.domain.services.*;

public class UsersApplication {
    public static void main(final String[] args) throws Exception {
        final String dbPath = System.getenv().getOrDefault("DB_PATH", "users.db");
        final String ingestUrl = System.getenv().getOrDefault("INGEST_URL", "http://insightbloom-ingest:8082");
        final String queryUrl = System.getenv().getOrDefault("QUERY_URL", "http://insightbloom-query:8083");
        final String moderationUrl = System.getenv().getOrDefault("MODERATION_URL", "http://insightbloom-moderation:8084");
        final String presentationsUrl = System.getenv().getOrDefault("PRESENTATIONS_URL", "http://insightbloom-presentations:8091");
        final String surveyUrl = System.getenv().getOrDefault("SURVEY_URL", "http://insightbloom-survey:8086");
        final String telegramUrl = System.getenv().getOrDefault("TELEGRAM_URL", "http://insightbloom-telegram:8095");
        final String internalApiKey = System.getenv().getOrDefault("INTERNAL_API_KEY", "");
        final String drawioBaseUrl = System.getenv().getOrDefault("DRAWIO_BASE_URL", "");
        final String excalidrawBaseUrl = System.getenv().getOrDefault("EXCALIDRAW_BASE_URL", "");
        final String etherpadBaseUrl = System.getenv().getOrDefault("ETHERPAD_BASE_URL", "");
        // Etherpad has two addresses: the public one is exposed to the browser through the
        // tools gateway, while server-side API calls must use the ClusterIP directly. Falling
        // back to the public URL keeps local/non-Kubernetes deployments compatible.
        final String etherpadApiBaseUrl = System.getenv().getOrDefault("ETHERPAD_API_BASE_URL", etherpadBaseUrl);
        final String etherpadApiKey = System.getenv().getOrDefault("ETHERPAD_API_KEY", "");
        // Debe permanecer estable mientras existan pads privados. Si no se define por separado,
        // se conserva compatibilidad con despliegues que ya sólo tienen ETHERPAD_API_KEY.
        final String etherpadPrivatePadSecret = System.getenv().getOrDefault(
                "ETHERPAD_PRIVATE_PAD_SECRET", etherpadApiKey);
        final String jaasAppId = System.getenv().getOrDefault("JAAS_APP_ID", "");
        final String jaasApiKeyId = System.getenv().getOrDefault("JAAS_API_KEY_ID", "");
        final String jaasPrivateKeyBase64 = System.getenv().getOrDefault("JAAS_PRIVATE_KEY", "");

        final String twilioAccountSid = System.getenv().getOrDefault("TWILIO_ACCOUNT_SID", "");
        final String twilioAuthToken = System.getenv().getOrDefault("TWILIO_AUTH_TOKEN", "");
        final String twilioFromNumber = System.getenv().getOrDefault("TWILIO_FROM_NUMBER", "");
        final String zohoSmtpHost = System.getenv().getOrDefault("ZOHO_SMTP_HOST", "smtp.zoho.com");
        final String zohoSmtpPort = System.getenv().getOrDefault("ZOHO_SMTP_PORT", "587");
        final String zohoUsername = System.getenv().getOrDefault("ZOHO_SMTP_USERNAME", "");
        final String zohoPassword = System.getenv().getOrDefault("ZOHO_SMTP_PASSWORD", "");
        final String zohoFromAddress = System.getenv().getOrDefault("ZOHO_FROM_ADDRESS", zohoUsername);

        // Admin user auto-seeded on startup if not present in the DB (ver AdminSeeder.java).
        // Credenciales inyectadas desde el secreto SOPS insightbloom-admin-auth via env vars.
        final String adminUsername = System.getenv("ADMIN_USERNAME");
        final String adminPassword = System.getenv("ADMIN_PASSWORD");

        final String frontendBaseUrl = System.getenv().getOrDefault("FRONTEND_BASE_URL", "https://insightbloom.v1.rafex.cloud");
        final var contactInfo = new NotifyDoubtAnsweredUseCase.ContactInfo(
                System.getenv().getOrDefault("CONTACT_NAME", "Raúl González (rafex)"),
                System.getenv().getOrDefault("CONTACT_EMAIL", "rafex@rafex.dev"),
                System.getenv().getOrDefault("CONTACT_LINKEDIN", "https://linkedin.com/in/soft-architect-raul-gonzalez"),
                System.getenv().getOrDefault("CONTACT_GITHUB", "https://github.com/rafex"),
                System.getenv().getOrDefault("CONTACT_BLOG", "https://theworldofrafex.blog/"),
                System.getenv().getOrDefault("CONTACT_TELEGRAM", "@rafex0"));

        // Infrastructure
        final var db = new DatabaseManager(dbPath);
        db.initialize();

        // Repositories
        final var userRepo = new SqliteUserRepository(db);
        final var guestRepo = new SqliteGuestUserRepository(db);
        final var tokenRepo = new SqliteTokenRepository(db);
        final var conferenceRepo = new SqliteConferenceRepository(db);
        final var otpRepo = new SqliteOtpCodeRepository(db);
        final var membershipRepo = new SqliteConferenceMembershipRepository(db);
        final var certificateSettingsRepo = new SqliteCertificateSettingsRepository(db);
        final var certificateTemplateRepo = new SqliteCertificateTemplateRepository(db);
        final var platformSettingsRepo = new dev.rafex.insightbloom.users.adapters.outbound.sqlite.SqlitePlatformSettingsRepository(
                db, new dev.rafex.insightbloom.users.adapters.outbound.crypto.AiApiKeyCipher(internalApiKey));
        // AI_SEED_DEFAULTS=false salta el seeding y deja las capacidades sin configurar tal cual
        // esten en la BD (heredando en silencio la config de chat); en true (default) rellena una
        // sola vez, por capacidad, un Prompt base y Guardarails reales -- nunca pisa lo que el
        // admin ya haya guardado explicitamente. Ver AiDefaultsSeeder.
        final boolean aiSeedDefaults = !"false".equalsIgnoreCase(System.getenv("AI_SEED_DEFAULTS"));
        dev.rafex.insightbloom.users.domain.services.AiDefaultsSeeder.seedIfNeeded(platformSettingsRepo, aiSeedDefaults);
        // Control de egress por dominio (2026-07): mismo mecanismo de seed-once que AI_SEED_DEFAULTS,
        // pero sembrando desde los valores que HOY vive en el ConfigMap (EGRESS_PROXY_ALLOWED_HOSTS/
        // BLOCKED_HOSTS) para no perder el punto de partida al mover la fuente de verdad a la BD.
        // Ver EgressPolicyDefaultsSeeder.
        final boolean egressPolicySeedDefaults = !"false".equalsIgnoreCase(System.getenv("EGRESS_POLICY_SEED_DEFAULTS"));
        dev.rafex.insightbloom.users.domain.services.EgressPolicyDefaultsSeeder.seedIfNeeded(platformSettingsRepo,
                egressPolicySeedDefaults, System.getenv("EGRESS_PROXY_ALLOWED_HOSTS"),
                System.getenv("EGRESS_PROXY_BLOCKED_HOSTS"));
        final var egressPolicyRepo = new dev.rafex.insightbloom.users.adapters.outbound.sqlite.SqliteEgressPolicyRepository(db);
        final var imagePolicyRepo = new dev.rafex.insightbloom.users.adapters.outbound.sqlite.SqliteImagePolicyRepository(db);
        final var notificationRepo = new SqliteNotificationRepository(db);
        // Registro compartido de streams SSE: NotificationHandler registra/desregistra conexiones
        // acá, y SendNotificationUseCase empuja por acá.
        final var notificationStreamRegistry = new dev.rafex.insightbloom.users.domain.services.NotificationStreamRegistry();
        final var listNotificationsUseCase = new ListNotificationsUseCase(notificationRepo);
        final var markNotificationReadUseCase = new MarkNotificationReadUseCase(notificationRepo);
        final var sendNotificationUseCase = new SendNotificationUseCase(notificationRepo, notificationStreamRegistry);
        final var downloadEventRepo = new SqliteDownloadEventRepository(db);
        final var timezoneRepo = new SqliteTimezoneRepository(db);
        final var reservationRepo = new SqliteReservationRepository(db);
        final var ticketRepo = new SqliteTicketRepository(db);
        final var venueSeatRepo = new SqliteVenueSeatRepository(db);
        final var eventTypeRepo = new SqliteEventTypeRepository(db);
        final var roleRepo = new SqliteRoleRepository(db);
        final var eventRoleRepo = new SqliteEventRoleRepository(db);
        final var sandboxRepo = new SqliteSandboxRepository(db);
        final var sandboxIncidentRepo = new dev.rafex.insightbloom.users.adapters.outbound.sqlite.SqliteSandboxIncidentRepository(db);
        final var toolDeviceSessionRepo = new SqliteToolDeviceSessionRepository(db);
        final var jaasUsageRepo = new SqliteJaasUsageRepository(db);
        final var deviceBlockRepo = new SqliteDeviceBlockRepository(db);
        final var platformDeviceBlockRepo = new SqlitePlatformDeviceBlockRepository(db);
        final var deviceFingerprintFlagRepo = new SqliteDeviceFingerprintFlagRepository(db);
        final var eventPermissionGuard = new dev.rafex.insightbloom.users.domain.services.EventPermissionGuard(
                eventRoleRepo, roleRepo);

        // Domain services
        final var tokenService = new TokenService(tokenRepo);
        final var deviceAccessGuard = new DeviceAccessGuard(toolDeviceSessionRepo, deviceBlockRepo);
        final var platformDeviceGuard = new PlatformDeviceGuard(tokenRepo, userRepo, platformDeviceBlockRepo);
        final var deviceFingerprintAuditor = new dev.rafex.insightbloom.users.domain.services.DeviceFingerprintAuditor(deviceFingerprintFlagRepo);
        final var passwordService = new PasswordService();
        // Admin user: crea el admin desde el secreto SOPS si no existe en la BD (idempotente).
        dev.rafex.insightbloom.users.domain.services.AdminSeeder.seedIfNeeded(
                userRepo, passwordService, adminUsername, adminPassword);
        final var friendlyIdService = new FriendlyIdService(conferenceRepo);
        final var cascadeDeletePort = new HttpCascadeDeleteClient(
                ingestUrl, queryUrl, moderationUrl, presentationsUrl, surveyUrl, internalApiKey);
        final var smsPort = new TwilioSmsClient(twilioAccountSid, twilioAuthToken, twilioFromNumber);
        final var emailPort = new ZohoEmailClient(zohoSmtpHost, zohoSmtpPort, zohoUsername, zohoPassword, zohoFromAddress);
        final var surveyPort = new HttpSurveyClient(surveyUrl);
        final var telegramNotifyPort = new HttpTelegramNotifyClient(telegramUrl, internalApiKey);
        final var etherpadPort = new dev.rafex.insightbloom.users.adapters.outbound.etherpadclient.HttpEtherpadPort(
                etherpadApiBaseUrl, etherpadApiKey);

        // Use cases
        final var loginUseCase = new LoginUseCase(userRepo, tokenService, passwordService, platformDeviceGuard, platformSettingsRepo);
        final var createGuestUseCase = new CreateGuestUseCase(guestRepo, conferenceRepo, tokenService, platformDeviceGuard, platformSettingsRepo);
        final var validateTokenUseCase = new ValidateTokenUseCase(tokenService, userRepo, guestRepo);
        final var getConferenceUseCase = new GetConferenceUseCase(conferenceRepo, cascadeDeletePort, membershipRepo);
        final var registerUseCase = new RegisterUseCase(userRepo, passwordService, platformDeviceGuard, platformSettingsRepo);
        final var sendOtpUseCase = new SendOtpUseCase(otpRepo, smsPort, emailPort);
        final var verifyOtpUseCase = new VerifyOtpUseCase(otpRepo, userRepo, tokenService);
        final var requestLoginOtpUseCase = new dev.rafex.insightbloom.users.application.usecases.RequestLoginOtpUseCase(
                userRepo, otpRepo, emailPort);
        final var verifyLoginOtpUseCase = new dev.rafex.insightbloom.users.application.usecases.VerifyLoginOtpUseCase(
                userRepo, otpRepo, tokenService);
        final var setAuthMethodUseCase = new dev.rafex.insightbloom.users.application.usecases.SetAuthMethodUseCase(
                userRepo, passwordService);
        final var getUserProfileUseCase = new GetUserProfileUseCase(userRepo);
        final var updateProfileUseCase = new UpdateProfileUseCase(userRepo);
        final var changePasswordUseCase = new ChangePasswordUseCase(userRepo, passwordService);
        final var setSeatingModeUseCase = new SetSeatingModeUseCase(conferenceRepo, reservationRepo);
        final var reserveGeneralUseCase = new ReserveGeneralUseCase(conferenceRepo, reservationRepo, userRepo);
        final var getMyTicketUseCase = new GetMyTicketUseCase(reservationRepo);
        final var cancelReservationUseCase = new CancelReservationUseCase(reservationRepo, conferenceRepo);
        final var listReservationsUseCase = new ListReservationsUseCase(conferenceRepo, reservationRepo);
        final var listConferenceAttendeesUseCase = new dev.rafex.insightbloom.users.application.usecases.ListConferenceAttendeesUseCase(
                membershipRepo, userRepo);
        final var checkInTicketUseCase = new CheckInTicketUseCase(reservationRepo);
        final var toolAccessRepo = new SqliteToolAccessRepository(db);
        final var toolAccessUseCase = new dev.rafex.insightbloom.users.application.usecases.ToolAccessUseCase(
                toolAccessRepo, listConferenceAttendeesUseCase);
        final var ticketUseCase = new TicketUseCase(
                conferenceRepo, eventTypeRepo, ticketRepo, membershipRepo, emailPort, frontendBaseUrl,
                reservationRepo, timezoneRepo, userRepo);
        final var createConferenceUseCase = new CreateConferenceUseCase(
                conferenceRepo, friendlyIdService, timezoneRepo, eventRoleRepo, ticketUseCase);
        final var setVenueMapUseCase = new SetVenueMapUseCase(conferenceRepo);
        final var defineVenueSeatsUseCase = new DefineVenueSeatsUseCase(conferenceRepo, venueSeatRepo, reservationRepo);
        final var getConferenceSeatMapUseCase = new GetConferenceSeatMapUseCase(venueSeatRepo, reservationRepo);
        final var reserveSeatUseCase = new ReserveSeatUseCase(
                conferenceRepo, reservationRepo, venueSeatRepo, userRepo, emailPort, frontendBaseUrl);
        final var joinConferenceUseCase = new JoinConferenceUseCase(
                getConferenceUseCase, membershipRepo, userRepo, emailPort, timezoneRepo,
                reserveGeneralUseCase, frontendBaseUrl, ticketUseCase, eventPermissionGuard, sendNotificationUseCase);
        final var getConferenceHistoryUseCase = new GetConferenceHistoryUseCase(membershipRepo, conferenceRepo);
        final var certificatePlatformData = new dev.rafex.insightbloom.users.domain.model.CertificatePlatformData(
                "InsightBloom", frontendBaseUrl,
                contactInfo.email(), contactInfo.github(), contactInfo.linkedin(),
                System.getenv().getOrDefault("CONTACT_TELEGRAM_GROUP", "https://t.me/tabernadelanoche"));
        final var certificateRenderer = new dev.rafex.insightbloom.users.adapters.outbound.presentationsclient.HttpCertificateRenderer(
                presentationsUrl, internalApiKey);
        final var generateCertificateUseCase = new GenerateCertificateUseCase(
                conferenceRepo, userRepo, surveyPort, certificateSettingsRepo, certificateTemplateRepo,
                certificateRenderer, certificatePlatformData);
        final var notifyDoubtAnsweredUseCase = new NotifyDoubtAnsweredUseCase(
                userRepo, conferenceRepo, emailPort, telegramNotifyPort, frontendBaseUrl, contactInfo);
        final var getCertificateSettingsUseCase = new GetCertificateSettingsUseCase(certificateSettingsRepo);
        final var saveCertificateSettingsUseCase = new SaveCertificateSettingsUseCase(certificateSettingsRepo);
        final var countAttendeesUseCase = new CountAttendeesUseCase(guestRepo);
        final var countRegisteredAttendeesUseCase = new CountRegisteredAttendeesUseCase(membershipRepo);
        final var countUniqueRegisteredAttendeesUseCase = new CountUniqueRegisteredAttendeesUseCase(conferenceRepo, membershipRepo);
        final var countActiveRegisteredAttendeesUseCase = new CountActiveRegisteredAttendeesUseCase(conferenceRepo, membershipRepo);
        final var updateConferenceUseCase = new UpdateConferenceUseCase(conferenceRepo);
        final var setConferenceActiveUseCase = new SetConferenceActiveUseCase(conferenceRepo);
        final var recordDownloadUseCase = new RecordDownloadUseCase(downloadEventRepo);
        final var getDownloadCountsUseCase = new GetDownloadCountsUseCase(downloadEventRepo);
        final var listUsersUseCase = new ListUsersUseCase(userRepo);
        final var adminUpdateUserUseCase = new AdminUpdateUserUseCase(userRepo);
        final var setUserStatusUseCase = new SetUserStatusUseCase(userRepo, tokenService);
        final var logoutUseCase = new LogoutUseCase(tokenService);
        final var refreshTokenUseCase = new RefreshTokenUseCase(tokenService, validateTokenUseCase);
        final var listTimezonesUseCase = new ListTimezonesUseCase(timezoneRepo);
        final var listEventTypesUseCase = new ListEventTypesUseCase(eventTypeRepo);
        final var createEventTypeUseCase = new CreateEventTypeUseCase(eventTypeRepo);
        final var updateEventTypeUseCase = new UpdateEventTypeUseCase(eventTypeRepo);
        final var setEventTypeActiveUseCase = new SetEventTypeActiveUseCase(eventTypeRepo);
        final var listRolesUseCase = new ListRolesUseCase(roleRepo);
        final var createRoleUseCase = new CreateRoleUseCase(roleRepo);
        final var updateRoleUseCase = new UpdateRoleUseCase(roleRepo);
        final var setRoleActiveUseCase = new SetRoleActiveUseCase(roleRepo);
        final var assignEventRoleUseCase = new AssignEventRoleUseCase(
                eventRoleRepo, roleRepo, userRepo, eventPermissionGuard, ticketUseCase);
        final var listEventRolesUseCase = new ListEventRolesUseCase(eventRoleRepo, userRepo, eventPermissionGuard);
        final var removeEventRoleUseCase = new RemoveEventRoleUseCase(eventRoleRepo, conferenceRepo, eventPermissionGuard);
        final var sendConferenceRemindersUseCase = new SendConferenceRemindersUseCase(
                conferenceRepo, userRepo, timezoneRepo, emailPort, reservationRepo, frontendBaseUrl, sendNotificationUseCase);
        final var purgeExpiredEventNotesUseCase = new PurgeExpiredEventNotesUseCase(
                conferenceRepo, timezoneRepo, etherpadPort);
        final var eventCapabilityGuard = new dev.rafex.insightbloom.users.domain.services.EventCapabilityGuard(eventTypeRepo);
        final var setEventTypeUseCase = new SetEventTypeUseCase(conferenceRepo, eventTypeRepo, reservationRepo);
        final var sendAttendeeEmailUseCase = new dev.rafex.insightbloom.users.application.usecases.SendAttendeeEmailUseCase(
                conferenceRepo, reservationRepo, userRepo, emailPort);
        final var notifyConferenceUpdatedUseCase = new dev.rafex.insightbloom.users.application.usecases.NotifyConferenceUpdatedUseCase(
                reservationRepo, userRepo, emailPort, eventTypeRepo, sendNotificationUseCase, frontendBaseUrl);
        final var setOnDemandVideoUseCase = new dev.rafex.insightbloom.users.application.usecases.SetOnDemandVideoUseCase(conferenceRepo);
        final var emailLlmClient = new EmailLlmClient(platformSettingsRepo, JacksonJsonCodec.defaultCodec());
        final var generateEmailDraftUseCase = new GenerateEmailDraftUseCase(conferenceRepo, emailLlmClient);
        final var getOrCreateEventPadUseCase = new GetOrCreateEventPadUseCase(
                conferenceRepo, etherpadPort, etherpadPrivatePadSecret);
        final var exportEventNotesUseCase = new ExportEventNotesUseCase(getOrCreateEventPadUseCase, etherpadPort);
        final var getEventDiagramUseCase = new GetEventDiagramUseCase(conferenceRepo);
        final var saveEventDiagramUseCase = new SaveEventDiagramUseCase(conferenceRepo);
        final var getEventWhiteboardUseCase = new GetEventWhiteboardUseCase(conferenceRepo);
        final var saveEventWhiteboardUseCase = new SaveEventWhiteboardUseCase(conferenceRepo);
        final int jaasMonthlyParticipantLimit = Integer.parseInt(
                System.getenv().getOrDefault("JAAS_MONTHLY_PARTICIPANT_LIMIT", "25"));
        final int jaasMonthlyBandwidthLimitGb = Integer.parseInt(
                System.getenv().getOrDefault("JAAS_MONTHLY_BANDWIDTH_LIMIT_GB", "200"));
        final var getJaasUsageUseCase = new GetJaasUsageUseCase(
                jaasUsageRepo, jaasMonthlyParticipantLimit, jaasMonthlyBandwidthLimitGb);
        final var eventMaterialsDownloadUseCase = new EventMaterialsDownloadUseCase(
                conferenceRepo, eventCapabilityGuard, getEventDiagramUseCase, getEventWhiteboardUseCase, etherpadPort);
        final var purgeExpiredEventDiagramsUseCase = new PurgeExpiredEventDiagramsUseCase(conferenceRepo, timezoneRepo);
        final var generateJaasTokenUseCase = new GenerateJaasTokenUseCase(
                jaasAppId, jaasApiKeyId, jaasPrivateKeyBase64, eventPermissionGuard, userRepo,
                conferenceRepo, deviceAccessGuard, ticketUseCase, jaasUsageRepo);
        final var llmClient = new dev.rafex.insightbloom.users.adapters.outbound.llm.GroqLlmClient(
                platformSettingsRepo, JacksonJsonCodec.defaultCodec());
        final var generateSeatLayoutUseCase = new GenerateSeatLayoutUseCase(llmClient, JacksonJsonCodec.defaultCodec());
        final var getChatAiSettingUseCase = new GetChatAiSettingUseCase(platformSettingsRepo);
        final var setChatAiSettingUseCase = new SetChatAiSettingUseCase(platformSettingsRepo);
        final var setChatSettingsUseCase = new SetChatSettingsUseCase(platformSettingsRepo);
        final var setAiSettingsUseCase = new SetAiSettingsUseCase(platformSettingsRepo);
        final var setDeviceAccessSettingsUseCase = new SetDeviceAccessSettingsUseCase(platformSettingsRepo);
        final var listPlatformDeviceBlocksUseCase = new ListPlatformDeviceBlocksUseCase(platformDeviceBlockRepo);
        final var unblockPlatformDeviceUseCase = new UnblockPlatformDeviceUseCase(platformDeviceBlockRepo);
        final var listDeviceFingerprintFlagsUseCase = new ListDeviceFingerprintFlagsUseCase(deviceFingerprintFlagRepo);
        final var reviewDeviceFingerprintFlagUseCase = new ReviewDeviceFingerprintFlagUseCase(deviceFingerprintFlagRepo);
        final String gatewayBaseUrl = System.getenv().getOrDefault("GATEWAY_BASE_URL", "https://ide-insightbloom.v1.rafex.cloud");
        // El link de descarga del workspace NO pasa por el tools-gateway (ese solo sabe proxear,
        // no puede armar un zip): apunta al host publico de este mismo servicio, proxeado por el
        // nginx del frontend (ver container/frontend/nginx.conf, location /api/users) -- mismo
        // dominio que ya usan usersApi.ts para el resto de la API.
        final String workspaceDownloadBaseUrl = System.getenv().getOrDefault(
                "WORKSPACE_DOWNLOAD_BASE_URL", "https://insightbloom.v1.rafex.cloud/api/users");
        final String sandboxDebianMemoryLimit = System.getenv().getOrDefault("SANDBOX_DEBIAN_MEMORY_LIMIT", "1536Mi");
        final String sandboxNeovimMemoryLimit = System.getenv().getOrDefault("SANDBOX_NEOVIM_MEMORY_LIMIT", "1Gi");
        final String sandboxPodmanMemoryLimit = System.getenv().getOrDefault("SANDBOX_PODMAN_MEMORY_LIMIT", "2Gi");
        // Fase 4b (MVP, 2026-08): banda de puertos DEDICADA al pod Podman compartido, separada de
        // SANDBOX_APP_BASE_PORT (ese es por-asiento de cada sandbox de evento; este es
        // por-publicacion del pod Podman único). Default lejos de los rangos existentes
        // (SANDBOX_PORT 8080.., SANDBOX_APP_BASE_PORT 9000..) para no colisionar.
        final int sandboxPodmanAppBasePort = Integer.parseInt(
                System.getenv().getOrDefault("SANDBOX_PODMAN_APP_BASE_PORT", "9500"));
        // Fase C (2026-07): secreto de BAJO privilegio (distinto de INTERNAL_API_KEY, ver
        // DEC-0025) compartido entre el Pod (KubernetesPodClient lo inyecta como env var) y
        // este proceso (InternalSandboxIncidentHandler lo valida) para que el watchdog del
        // seat-agent pueda reportar incidentes.
        // AUD-04: sin fallback -- si la env var no esta seteada, el handler (que ya rechaza
        // header==null) queda cerrado por defecto en vez de aceptar un valor predecible.
        final String sandboxIncidentReportKey = System.getenv("SANDBOX_INCIDENT_REPORT_KEY");
        final var sandboxOrchestrator = new dev.rafex.insightbloom.users.adapters.outbound.kubernetes.KubernetesPodClient(
                JacksonJsonCodec.defaultCodec(),
                System.getenv().getOrDefault("SANDBOX_NAMESPACE", "insightbloom-sandboxes"),
                // GitOps sobreescribe ambas referencias con builds inmutables y precargados.
                // El fallback conservado sirve para instalaciones sin GitOps: primero intenta
                // la cache local (IfNotPresent) y solo entonces consulta GHCR con latest.
                System.getenv().getOrDefault("SANDBOX_DEBIAN_IMAGE", "ghcr.io/rafex/insightbloom-code-ide-debian:latest"),
                System.getenv().getOrDefault("SANDBOX_NEOVIM_IMAGE", "ghcr.io/rafex/insightbloom-code-ide-neovim:latest"),
                System.getenv().getOrDefault("SANDBOX_NEOVIM_LAZYVIM_IMAGE", "ghcr.io/rafex/insightbloom-code-ide-neovim-lazyvim:latest"),
                System.getenv().getOrDefault("SANDBOX_IMAGE_PULL_POLICY", "IfNotPresent"),
                System.getenv("SANDBOX_PRIORITY_CLASS"),
                // Cambio de paradigma 2026-07-17: un solo contenedor por Pod (ver
                // KubernetesPodClient), pero DOS sets de recursos -- uno por imagen, no uno
                // compartido: las imagenes tienen perfiles de consumo muy distintos. Debian
                // corre el extension host de VS Code Web (proceso Node) + code-server + jdt.ls
                // (Language Server de Java, una JVM completa) -- la carga pesada. Alpine solo
                // corre ttyd+nvim (un proceso liviano, sin el overhead de VS Code Web), aunque
                // tambien puede activar jdt.ls al editar .java. El namespace
                // insightbloom-sandboxes tiene su LimitRange (kubectl, no versionado en este
                // repo) con tope de Pod en 2048Mi/1000m desde el ajuste del 2026-07-17 -- los
                // dos sets quedan con margen debajo de ese tope.
                new dev.rafex.insightbloom.users.adapters.outbound.kubernetes.KubernetesPodClient.ContainerResources(
                        System.getenv().getOrDefault("SANDBOX_DEBIAN_CPU_REQUEST", "500m"),
                        System.getenv().getOrDefault("SANDBOX_DEBIAN_MEMORY_REQUEST", "1Gi"),
                        System.getenv().getOrDefault("SANDBOX_DEBIAN_CPU_LIMIT", "750m"),
                        sandboxDebianMemoryLimit),
                new dev.rafex.insightbloom.users.adapters.outbound.kubernetes.KubernetesPodClient.ContainerResources(
                        System.getenv().getOrDefault("SANDBOX_NEOVIM_CPU_REQUEST", "350m"),
                        System.getenv().getOrDefault("SANDBOX_NEOVIM_MEMORY_REQUEST", "768Mi"),
                        System.getenv().getOrDefault("SANDBOX_NEOVIM_CPU_LIMIT", "500m"),
                        sandboxNeovimMemoryLimit),
                Integer.parseInt(System.getenv().getOrDefault("SANDBOX_PORT", "8080")),
                Integer.parseInt(System.getenv().getOrDefault("SANDBOX_UID", "1000")),
                Integer.parseInt(System.getenv().getOrDefault("SANDBOX_GID", "1000")),
                Integer.parseInt(System.getenv().getOrDefault("SANDBOX_FSGROUP", "1000")),
                // Ver KubernetesPodClient.ensureIngressPolicy: restringe quien puede conectarse
                // ENTRANTE a un sandbox al Pod del gateway unicamente (namespace + label) --
                // auditoria de seguridad 2026-07-17, cierra acceso Pod-a-Pod entre sandboxes.
                System.getenv().getOrDefault("SANDBOX_GATEWAY_NAMESPACE", "insightbloom"),
                System.getenv().getOrDefault("SANDBOX_GATEWAY_POD_COMPONENT_LABEL", "toolsgateway"),
                // Fase B (2026-07): puerto de control del seat-agent en Pods neovim
                // multi-asiento -- solo insightbloom-users puede llamarlo (ver
                // KubernetesPodClient.ensureIngressPolicy, segunda regla de Ingress).
                System.getenv().getOrDefault("SANDBOX_USERS_POD_COMPONENT_LABEL", "users"),
                sandboxIncidentReportKey,
                System.getenv().getOrDefault("SANDBOX_EGRESS_PROXY_HOST",
                        "insightbloom-egress-proxy.insightbloom.svc.cluster.local"),
                Integer.parseInt(System.getenv().getOrDefault("SANDBOX_EGRESS_PROXY_PORT", "3128")),
                // Publicacion de backends/API REST vivos (2026-07): banda de puertos paralela a
                // SANDBOX_PORT (8080..8089), separada a proposito para no colisionar con los
                // puertos de ttyd/code-server ni con el control port (SANDBOX_PORT - 1).
                Integer.parseInt(System.getenv().getOrDefault("SANDBOX_APP_BASE_PORT", "9000")),
                // Fase 4b (MVP): pod Podman compartido, ver ensureRuntimePodmanPod. Imagen propia
                // (toolchain de Podman, ver infra/docker/Dockerfile.runtime-podman), no relacionada
                // con debian/neovim.
                System.getenv().getOrDefault("SANDBOX_PODMAN_IMAGE", "ghcr.io/rafex/insightbloom-runtime-podman:latest"),
                new dev.rafex.insightbloom.users.adapters.outbound.kubernetes.KubernetesPodClient.ContainerResources(
                        System.getenv().getOrDefault("SANDBOX_PODMAN_CPU_REQUEST", "500m"),
                        System.getenv().getOrDefault("SANDBOX_PODMAN_MEMORY_REQUEST", "1Gi"),
                        System.getenv().getOrDefault("SANDBOX_PODMAN_CPU_LIMIT", "1000m"),
                        sandboxPodmanMemoryLimit),
                sandboxPodmanAppBasePort,
                System.getenv().getOrDefault("SANDBOX_PODMAN_STORAGE_SIZE_LIMIT", "4Gi"),
                // Fase 7 (2026-08): CIDR interno del cluster que el initContainer de cada sandbox
                // permite alcanzar (nftables) antes de bloquear el resto del egress -- default de
                // k3s (pods 10.42.0.0/16 + services 10.43.0.0/16, ambos dentro de 10.0.0.0/8).
                // Configurable por si el cluster real usa otro rango.
                System.getenv().getOrDefault("SANDBOX_CLUSTER_CIDR", "10.0.0.0/8"));
        final long sandboxTtlSecondsAfterEventExpiry =
                Long.parseLong(System.getenv().getOrDefault("SANDBOX_TTL_SECONDS_AFTER_EVENT_EXPIRY", "3600"));
        final var assignSandboxUseCase = new AssignSandboxUseCase(
                sandboxRepo, conferenceRepo, sandboxOrchestrator, deviceAccessGuard, sandboxTtlSecondsAfterEventExpiry);
        final var getSandboxAvailabilityUseCase = new GetSandboxAvailabilityUseCase(conferenceRepo, sandboxRepo);
        final var ensureUnassignedSandboxUseCase = new EnsureUnassignedSandboxUseCase(
                sandboxRepo, conferenceRepo, sandboxOrchestrator, sandboxTtlSecondsAfterEventExpiry);
        final var prewarmSandboxPoolUseCase = new PrewarmSandboxPoolUseCase(
                conferenceRepo, ensureUnassignedSandboxUseCase);
        final var resetSandboxUseCase = new ResetSandboxUseCase(
                sandboxRepo, conferenceRepo, sandboxOrchestrator, ensureUnassignedSandboxUseCase);
        // AUD-07: WORKSPACE_DOWNLOAD_SECRET no tiene un default predecible (ver AUD-04) -- si no
        // esta seteada, se genera una al azar en cada arranque. Como los tokens duran 2 minutos
        // (WorkspaceDownloadToken.EXPIRY_SECONDS) y este servicio corre con una sola replica, un
        // secreto efimero por proceso es tan seguro como uno persistido, sin el riesgo de un
        // valor por defecto adivinable.
        final String workspaceDownloadSecret = java.util.Optional.ofNullable(System.getenv("WORKSPACE_DOWNLOAD_SECRET"))
                .filter(s -> !s.isBlank())
                .orElseGet(() -> {
                    final byte[] random = new byte[32];
                    new java.security.SecureRandom().nextBytes(random);
                    return java.util.Base64.getEncoder().encodeToString(random);
                });
        final var workspaceDownloadTokenCodec = new WorkspaceDownloadToken(workspaceDownloadSecret);
        final var generateWorkspaceDownloadUrlUseCase = new GenerateWorkspaceDownloadUrlUseCase(
                sandboxRepo, workspaceDownloadBaseUrl, workspaceDownloadTokenCodec);
        final var downloadWorkspaceZipUseCase = new DownloadWorkspaceZipUseCase(
                sandboxRepo, sandboxOrchestrator, workspaceDownloadTokenCodec);
        final var workspaceZipJobRepo = new SqliteWorkspaceZipJobRepository(db);
        final var workspaceZipCache = new dev.rafex.insightbloom.users.domain.services.WorkspaceZipCache();
        // Un armado de zip es CPU/red-bound y puede tardar hasta 90s (ver KubernetesPodClient) --
        // pool chico y separado del resto de los ScheduledExecutorService de este arranque, para
        // no competir por hilos con los ticks periódicos de sandbox/reminders.
        final var workspaceZipExecutor = java.util.concurrent.Executors.newFixedThreadPool(4, runnable -> {
            final var t = new Thread(runnable, "workspace-zip-builder");
            t.setDaemon(true);
            return t;
        });
        final var startWorkspaceZipJobUseCase = new StartWorkspaceZipJobUseCase(
                sandboxRepo, conferenceRepo, userRepo, workspaceZipJobRepo, workspaceZipCache,
                sandboxOrchestrator, sendNotificationUseCase, emailPort, workspaceZipExecutor,
                workspaceDownloadBaseUrl);
        final var getWorkspaceZipJobStatusUseCase = new GetWorkspaceZipJobStatusUseCase(
                workspaceZipJobRepo, workspaceDownloadBaseUrl);
        final var downloadWorkspaceZipJobUseCase = new DownloadWorkspaceZipJobUseCase(
                workspaceZipJobRepo, workspaceZipCache);
        final var cleanupExpiredWorkspaceZipJobsUseCase = new CleanupExpiredWorkspaceZipJobsUseCase(
                workspaceZipJobRepo, workspaceZipCache);
        final var workspacePreviewPublisher =
                new dev.rafex.insightbloom.users.adapters.outbound.presentationsclient.HttpWorkspacePreviewPublisher(
                        presentationsUrl, internalApiKey);
        final var publishWorkspacePreviewUseCase = new PublishWorkspacePreviewUseCase(
                sandboxRepo, sandboxOrchestrator, workspacePreviewPublisher);
        final var revokeWorkspacePreviewUseCase = new RevokeWorkspacePreviewUseCase(workspacePreviewPublisher);
        final long workspacePreviewTtlSeconds = Long.parseLong(
                System.getenv().getOrDefault("WORKSPACE_PREVIEW_TTL_SECONDS", "3600"));
        final var appPreviewRepo =
                new dev.rafex.insightbloom.users.adapters.outbound.sqlite.SqliteSandboxAppPreviewRepository(db);
        final var publishAppPreviewUseCase = new dev.rafex.insightbloom.users.application.usecases.PublishAppPreviewUseCase(
                sandboxRepo, appPreviewRepo,
                Integer.parseInt(System.getenv().getOrDefault("SANDBOX_APP_BASE_PORT", "9000")));
        final var revokeAppPreviewUseCase = new dev.rafex.insightbloom.users.application.usecases.RevokeAppPreviewUseCase(
                appPreviewRepo);
        final long appPreviewTtlSeconds = Long.parseLong(
                System.getenv().getOrDefault("APP_PREVIEW_TTL_SECONDS", "3600"));
        final String appPreviewBaseUrl = System.getenv().getOrDefault(
                "APP_PREVIEW_BASE_URL", "https://app-insightbloom.v1.rafex.cloud");
        final var resolveAppPreviewTargetUseCase =
                new dev.rafex.insightbloom.users.application.usecases.ResolveAppPreviewTargetUseCase(
                        appPreviewRepo, System.getenv().getOrDefault("SANDBOX_NAMESPACE", "insightbloom-sandboxes"));
        // Fase 4b (MVP): mismo resolver de política de imágenes de Fase 4a (ImagePolicyUseCase,
        // instanciado más abajo, es el editor del organizador; este es el consumidor real, el
        // primero desde que se agregó ContainerfileValidator).
        final var resolveImagePolicyUseCase =
                new dev.rafex.insightbloom.users.application.usecases.ResolveImagePolicyUseCase(
                        platformSettingsRepo, imagePolicyRepo);
        final var publishContainerUseCase = new dev.rafex.insightbloom.users.application.usecases.PublishContainerUseCase(
                sandboxRepo, sandboxOrchestrator, appPreviewRepo, resolveImagePolicyUseCase,
                System.getenv().getOrDefault("SANDBOX_PODMAN_SHARED_POD_NAME", "sandbox-runtime-podman-shared"),
                sandboxPodmanAppBasePort,
                Integer.parseInt(System.getenv().getOrDefault("SANDBOX_PODMAN_MAX_PUBLICATIONS", "10")));
        final var setSandboxInternetUseCase = new SetSandboxInternetUseCase(conferenceRepo);
        final var purgeSandboxPoolUseCase = new PurgeSandboxPoolUseCase(sandboxRepo, sandboxOrchestrator);
        final var reconcileSandboxHealthUseCase = new dev.rafex.insightbloom.users.application.usecases.ReconcileSandboxHealthUseCase(
                sandboxRepo, conferenceRepo, sandboxOrchestrator);
        final var resolveSandboxTargetUseCase = new ResolveSandboxTargetUseCase(
                validateTokenUseCase, sandboxRepo,
                System.getenv().getOrDefault("SANDBOX_NAMESPACE", "insightbloom-sandboxes"),
                Integer.parseInt(System.getenv().getOrDefault("SANDBOX_PORT", "8080")));
        final var recordSandboxIncidentUseCase = new dev.rafex.insightbloom.users.application.usecases.RecordSandboxIncidentUseCase(sandboxIncidentRepo);
        final var listSandboxIncidentsUseCase = new dev.rafex.insightbloom.users.application.usecases.ListSandboxIncidentsUseCase(sandboxIncidentRepo);
        final var listSandboxStatusUseCase = new ListSandboxStatusUseCase(sandboxRepo, sandboxOrchestrator, userRepo);
        final var listWorkspaceFilesUseCase = new ListWorkspaceFilesUseCase(sandboxRepo, sandboxOrchestrator);
        final var readWorkspaceFileUseCase = new ReadWorkspaceFileUseCase(sandboxRepo, sandboxOrchestrator);
        final var writeWorkspaceFileUseCase = new WriteWorkspaceFileUseCase(sandboxRepo, sandboxOrchestrator);

        // AUD-08: mismo criterio que WORKSPACE_DOWNLOAD_SECRET (linea ~313) -- el codigo de
        // intercambio SSO del chat dura 60s (SsoExchangeToken.EXPIRY_SECONDS) y este servicio
        // corre con una sola replica, asi que un secreto efimero por proceso es tan seguro como
        // uno persistido, sin el riesgo de un valor por defecto adivinable.
        final String ssoExchangeSecret = java.util.Optional.ofNullable(System.getenv("SSO_EXCHANGE_SECRET"))
                .filter(s -> !s.isBlank())
                .orElseGet(() -> {
                    final byte[] random = new byte[32];
                    new java.security.SecureRandom().nextBytes(random);
                    return java.util.Base64.getEncoder().encodeToString(random);
                });
        final var ssoExchangeTokenCodec = new dev.rafex.insightbloom.users.application.usecases.SsoExchangeToken(ssoExchangeSecret);
        final var createSsoExchangeUseCase = new dev.rafex.insightbloom.users.application.usecases.CreateSsoExchangeUseCase(
                validateTokenUseCase, ssoExchangeTokenCodec);
        final var consumeSsoExchangeUseCase = new dev.rafex.insightbloom.users.application.usecases.ConsumeSsoExchangeUseCase(
                ssoExchangeTokenCodec);

        // Handlers
        final var authHandler = new AuthHandler(loginUseCase, createGuestUseCase, validateTokenUseCase,
                registerUseCase, sendOtpUseCase, verifyOtpUseCase, logoutUseCase, refreshTokenUseCase,
                createSsoExchangeUseCase, consumeSsoExchangeUseCase, requestLoginOtpUseCase, verifyLoginOtpUseCase);
        final int maxPoolSizePerEvent = Integer.parseInt(System.getenv().getOrDefault("SANDBOX_POOL_MAX_PER_EVENT", "50"));
        // Techo de -Xmx configurable desde el Dashboard (SetSandboxConfigUseCase): limite de
        // memoria del contenedor menos margen para el resto del proceso -- 400Mi para Debian
        // (code-server + extension host de VS Code Web corren ahi tambien, no solo la JVM del
        // heap), 150Mi para Alpine (solo ttyd+nvim, mucho mas liviano). El heap NUNCA puede
        // pisar esta cuenta o el contenedor entero se cae por OOM del lado del sistema operativo,
        // no de la JVM (que respetaria su propio -Xmx sin problema).
        final int maxJvmHeapMbDebian = Math.max(64, parseK8sMemoryToMb(sandboxDebianMemoryLimit) - 400);
        final int maxJvmHeapMbNeovim = Math.max(64, parseK8sMemoryToMb(sandboxNeovimMemoryLimit) - 150);
        final var setSandboxConfigUseCase = new SetSandboxConfigUseCase(
                conferenceRepo, maxPoolSizePerEvent, maxJvmHeapMbDebian, maxJvmHeapMbNeovim);
        final var egressPolicyUseCase =
                new dev.rafex.insightbloom.users.application.usecases.EgressPolicyUseCase(egressPolicyRepo);
        final var imagePolicyUseCase =
                new dev.rafex.insightbloom.users.application.usecases.ImagePolicyUseCase(imagePolicyRepo);
        final var setCanvasConfigUseCase = new dev.rafex.insightbloom.users.application.usecases.SetCanvasConfigUseCase(conferenceRepo);
        final var setDeviceAccessConfigUseCase = new SetDeviceAccessConfigUseCase(conferenceRepo);
        final var listDeviceBlocksUseCase = new ListDeviceBlocksUseCase(deviceBlockRepo);
        final var unblockDeviceUseCase = new UnblockDeviceUseCase(deviceBlockRepo);
        final var sandboxHandler = new SandboxHandler(
                assignSandboxUseCase, getSandboxAvailabilityUseCase, validateTokenUseCase,
                generateWorkspaceDownloadUrlUseCase, startWorkspaceZipJobUseCase, getWorkspaceZipJobStatusUseCase,
                setSandboxConfigUseCase, sandboxOrchestrator,
                conferenceRepo, eventCapabilityGuard, toolAccessUseCase, ensureUnassignedSandboxUseCase, gatewayBaseUrl,
                publishWorkspacePreviewUseCase, revokeWorkspacePreviewUseCase, workspacePreviewTtlSeconds,
                publishAppPreviewUseCase, revokeAppPreviewUseCase, appPreviewTtlSeconds, appPreviewBaseUrl,
                publishContainerUseCase);
        final var sandboxFilesHandler = new SandboxFilesHandler(
                validateTokenUseCase, listWorkspaceFilesUseCase, readWorkspaceFileUseCase, writeWorkspaceFileUseCase,
                getConferenceUseCase);
        final var workspaceDownloadHandler = new WorkspaceDownloadHandler(
                downloadWorkspaceZipJobUseCase, downloadWorkspaceZipUseCase);
        final var conferenceHandler = new ConferenceHandler(createConferenceUseCase, getConferenceUseCase,
                validateTokenUseCase, joinConferenceUseCase, getConferenceHistoryUseCase, generateCertificateUseCase,
                countAttendeesUseCase, countRegisteredAttendeesUseCase, countUniqueRegisteredAttendeesUseCase,
                countActiveRegisteredAttendeesUseCase,
                updateConferenceUseCase, setConferenceActiveUseCase,
                recordDownloadUseCase, getDownloadCountsUseCase,
                setSeatingModeUseCase, reserveGeneralUseCase, getMyTicketUseCase, cancelReservationUseCase,
                listReservationsUseCase, listConferenceAttendeesUseCase, checkInTicketUseCase,
                ticketUseCase, eventPermissionGuard, createGuestUseCase,
                setVenueMapUseCase, defineVenueSeatsUseCase, getConferenceSeatMapUseCase, reserveSeatUseCase,
                setEventTypeUseCase, setCanvasConfigUseCase,
                eventCapabilityGuard, getOrCreateEventPadUseCase,
                exportEventNotesUseCase,
                eventMaterialsDownloadUseCase,
                assignEventRoleUseCase, listEventRolesUseCase, removeEventRoleUseCase,
                getEventDiagramUseCase, saveEventDiagramUseCase,
                getEventWhiteboardUseCase, saveEventWhiteboardUseCase,
                generateJaasTokenUseCase, generateSeatLayoutUseCase,
                setSandboxConfigUseCase, egressPolicyUseCase, imagePolicyUseCase, setSandboxInternetUseCase, ensureUnassignedSandboxUseCase,
                prewarmSandboxPoolUseCase,
                resetSandboxUseCase,
                listSandboxIncidentsUseCase, listSandboxStatusUseCase,
                setDeviceAccessConfigUseCase, listDeviceBlocksUseCase, unblockDeviceUseCase,
                sandboxHandler, sandboxFilesHandler, userRepo, deviceAccessGuard, getJaasUsageUseCase,
                toolAccessUseCase, sendAttendeeEmailUseCase, generateEmailDraftUseCase, notifyConferenceUpdatedUseCase,
                setOnDemandVideoUseCase);
        final var userProfileHandler = new UserProfileHandler(getUserProfileUseCase, updateProfileUseCase,
                validateTokenUseCase, changePasswordUseCase, setAuthMethodUseCase);
        final var notifyHandler = new NotifyHandler(notifyDoubtAnsweredUseCase);
        final var notificationHandler = new NotificationHandler(
                listNotificationsUseCase, markNotificationReadUseCase, validateTokenUseCase, notificationStreamRegistry);
        final var certificateSettingsHandler = new CertificateSettingsHandler(
                getCertificateSettingsUseCase, saveCertificateSettingsUseCase, validateTokenUseCase);
        final var certificateTemplateHandler = new CertificateTemplateHandler(
                certificateTemplateRepo, conferenceRepo, eventPermissionGuard, validateTokenUseCase,
                certificateSettingsRepo);
        final var listUserReservationsUseCase =
                new ListUserReservationsUseCase(reservationRepo, ticketRepo, conferenceRepo, downloadEventRepo);
        final var adminUserHandler = new AdminUserHandler(
                listUsersUseCase, adminUpdateUserUseCase, setUserStatusUseCase, validateTokenUseCase, userRepo,
                listUserReservationsUseCase);
        final var timezoneHandler = new TimezoneHandler(listTimezonesUseCase);
        final var eventTypeHandler = new EventTypeHandler(listEventTypesUseCase, createEventTypeUseCase,
                updateEventTypeUseCase, setEventTypeActiveUseCase, validateTokenUseCase);
        final var eventCapabilityHandler = new EventCapabilityHandler();
        final var integrationConfigHandler = new IntegrationConfigHandler(
                drawioBaseUrl, etherpadBaseUrl, jaasAppId, excalidrawBaseUrl);
        final var roleHandler = new RoleHandler(listRolesUseCase, createRoleUseCase, updateRoleUseCase,
                setRoleActiveUseCase, validateTokenUseCase);
        final var permissionHandler = new PermissionHandler();
        final var setGlobalEgressPolicyUseCase =
                new dev.rafex.insightbloom.users.application.usecases.SetGlobalEgressPolicyUseCase(platformSettingsRepo);
        final var resolveEgressPolicyUseCase =
                new dev.rafex.insightbloom.users.application.usecases.ResolveEgressPolicyUseCase(
                        sandboxOrchestrator, conferenceRepo, platformSettingsRepo, egressPolicyRepo);
        final var setGlobalImagePolicyUseCase =
                new dev.rafex.insightbloom.users.application.usecases.SetGlobalImagePolicyUseCase(platformSettingsRepo);
        // ResolveImagePolicyUseCase/PublishContainerUseCase: instanciados más arriba (Fase 4b,
        // MVP con hostUsers:false, sin prerequisito de infra externo -- ver KubernetesPodClient).
        final var platformSettingsHandler = new PlatformSettingsHandler(
                getChatAiSettingUseCase, setChatAiSettingUseCase, setChatSettingsUseCase, setAiSettingsUseCase,
                setDeviceAccessSettingsUseCase, setGlobalEgressPolicyUseCase, setGlobalImagePolicyUseCase,
                listPlatformDeviceBlocksUseCase,
                unblockPlatformDeviceUseCase, listDeviceFingerprintFlagsUseCase, reviewDeviceFingerprintFlagUseCase,
                validateTokenUseCase);
        final var internalSandboxTargetHandler = new InternalSandboxTargetHandler(resolveSandboxTargetUseCase);
        final var internalSandboxIncidentHandler =
                new dev.rafex.insightbloom.users.adapters.inbound.http.handlers.InternalSandboxIncidentHandler(
                        recordSandboxIncidentUseCase, sandboxIncidentReportKey);
        final var internalEgressPolicyHandler =
                new dev.rafex.insightbloom.users.adapters.inbound.http.handlers.InternalEgressPolicyHandler(
                        resolveEgressPolicyUseCase);
        final var internalAppPreviewTargetHandler =
                new dev.rafex.insightbloom.users.adapters.inbound.http.handlers.InternalAppPreviewTargetHandler(
                        resolveAppPreviewTargetUseCase);

        // Route registry
        final var routes = new JettyRouteRegistry();
        routes.add("/api/v1/auth/*", authHandler);
        routes.add("/workspaces/*", workspaceDownloadHandler);
        routes.add("/internal/sandbox-target/*", internalSandboxTargetHandler);
        routes.add("/internal/sandbox-incidents/*", internalSandboxIncidentHandler);
        routes.add("/internal/egress-policy/*", internalEgressPolicyHandler);
        routes.add("/internal/app-preview-target/*", internalAppPreviewTargetHandler);
        routes.add("/api/v1/conferences/*", conferenceHandler);
        routes.add("/api/v1/users/*", userProfileHandler);
        routes.add("/api/v1/notify/*", notifyHandler);
        routes.add("/api/v1/notifications/*", notificationHandler);
        routes.add("/api/v1/certificate-settings/*", certificateSettingsHandler);
        routes.add("/api/v1/certificate-templates/*", certificateTemplateHandler);
        routes.add("/api/v1/admin/users/*", adminUserHandler);
        routes.add("/api/v1/timezones/*", timezoneHandler);
        routes.add("/api/v1/event-types/*", eventTypeHandler);
        routes.add("/api/v1/event-capabilities/*", eventCapabilityHandler);
        routes.add("/api/v1/integrations/*", integrationConfigHandler);
        routes.add("/api/v1/roles/*", roleHandler);
        routes.add("/api/v1/permissions/*", permissionHandler);
        routes.add("/api/v1/settings/*", platformSettingsHandler);
        routes.add("/version", new VersionHandler("insightbloom-users"));

        // Server
        final var codec = JacksonJsonCodec.defaultCodec();
        final var config = JettyServerConfig.fromEnv();
        final var deviceFingerprintAuditMiddleware = java.util.List.<dev.rafex.ether.http.jetty12.middleware.JettyMiddleware>of(
                next -> new dev.rafex.insightbloom.users.adapters.inbound.http.middleware.DeviceFingerprintAuditHandler(
                        next, tokenService, deviceFingerprintAuditor));
        final var runner = JettyServerFactory.create(config, routes, codec, null, java.util.List.of(), deviceFingerprintAuditMiddleware);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try { runner.stop(); } catch (final Exception e) { e.printStackTrace(); }
        }));

        // Recordatorio de conferencia 1h antes: revisa cada 5 min, sin CronJob/infra nueva —
        // el estado (reminder_sent_at) vive en la propia tabla conferences, así que un reinicio
        // del pod no reenvía ni pierde recordatorios pendientes.
        final var reminderScheduler = java.util.concurrent.Executors.newSingleThreadScheduledExecutor(r -> {
            final var t = new Thread(r, "conference-reminder-scheduler");
            t.setDaemon(true);
            return t;
        });
        final var sandboxHealthScheduler = java.util.concurrent.Executors.newSingleThreadScheduledExecutor(r -> {
            final var t = new Thread(r, "sandbox-health-scheduler");
            t.setDaemon(true);
            return t;
        });
        final long sandboxHealthIntervalSeconds = Long.parseLong(
                System.getenv().getOrDefault("SANDBOX_HEALTH_RECONCILE_INTERVAL_SECONDS", "30"));
        sandboxHealthScheduler.scheduleAtFixedRate(() -> {
            try {
                final int recovered = reconcileSandboxHealthUseCase.execute(java.time.Instant.now());
                if (recovered > 0) System.out.println("sandbox-health-scheduler: recovered " + recovered + " sandboxes");
            } catch (final Exception e) {
                System.err.println("sandbox-health-scheduler: tick failed: " + e.getMessage());
            }
        }, sandboxHealthIntervalSeconds, sandboxHealthIntervalSeconds, java.util.concurrent.TimeUnit.SECONDS);
        // Cumple el TTL de 2hs de los zips de workspace pedidos async -- ver
        // CleanupExpiredWorkspaceZipJobsUseCase. Cada 10 min alcanza de sobra dado el TTL de horas.
        final var workspaceZipCleanupScheduler = java.util.concurrent.Executors.newSingleThreadScheduledExecutor(r -> {
            final var t = new Thread(r, "workspace-zip-cleanup-scheduler");
            t.setDaemon(true);
            return t;
        });
        workspaceZipCleanupScheduler.scheduleAtFixedRate(() -> {
            try {
                final int cleaned = cleanupExpiredWorkspaceZipJobsUseCase.execute(java.time.Instant.now());
                if (cleaned > 0) System.out.println("workspace-zip-cleanup-scheduler: cleaned " + cleaned + " jobs");
            } catch (final Exception e) {
                System.err.println("workspace-zip-cleanup-scheduler: tick failed: " + e.getMessage());
            }
        }, 10, 10, java.util.concurrent.TimeUnit.MINUTES);
        reminderScheduler.scheduleAtFixedRate(() -> {
            try {
                sendConferenceRemindersUseCase.execute(java.time.Instant.now());
            } catch (final Exception e) {
                System.err.println("conference-reminder-scheduler: tick failed: " + e.getMessage());
            }
            try {
                purgeExpiredEventNotesUseCase.execute(java.time.Instant.now());
            } catch (final Exception e) {
                System.err.println("event-notes-purge-scheduler: tick failed: " + e.getMessage());
            }
            try {
                purgeExpiredEventDiagramsUseCase.execute(java.time.Instant.now());
            } catch (final Exception e) {
                System.err.println("event-diagram-purge-scheduler: tick failed: " + e.getMessage());
            }
            try {
                final int deleted = purgeSandboxPoolUseCase.execute(java.time.Instant.now());
                if (deleted > 0) {
                    System.out.println("sandbox-pool-purge-scheduler: deleted " + deleted + " expired sandboxes");
                }
            } catch (final Exception e) {
                System.err.println("sandbox-pool-purge-scheduler: tick failed: " + e.getMessage());
            }
            try {
                final int expired = ticketUseCase.expireTickets(java.time.Instant.now());
                if (expired > 0) {
                    System.out.println("ticket-expiration-scheduler: expired tickets in " + expired + " conferences");
                }
            } catch (final Exception e) {
                System.err.println("ticket-expiration-scheduler: tick failed: " + e.getMessage());
            }
        }, 1, 5, java.util.concurrent.TimeUnit.MINUTES);

        runner.start();
        runner.await();
    }

    /**
     * Parsea una quantity de recursos de Kubernetes (ej. "896Mi", "1Gi", "512M") a MB enteros.
     * Solo soporta los sufijos que usamos en este archivo para limites de memoria de sandbox
     * (Mi/Gi binarios, M/G decimales, o sin sufijo = bytes) -- no es un parser general de la
     * spec completa de quantities de K8s (no hay Ki/Ti/m fraccionario, no hacen falta aca).
     */
    private static int parseK8sMemoryToMb(final String quantity) {
        final String q = quantity.trim();
        if (q.endsWith("Mi")) return Integer.parseInt(q.substring(0, q.length() - 2));
        if (q.endsWith("Gi")) return Integer.parseInt(q.substring(0, q.length() - 2)) * 1024;
        if (q.endsWith("M")) return (int) (Long.parseLong(q.substring(0, q.length() - 1)) * 1_000_000L / (1024 * 1024));
        if (q.endsWith("G")) return (int) (Long.parseLong(q.substring(0, q.length() - 1)) * 1_000_000_000L / (1024 * 1024));
        return (int) (Long.parseLong(q) / (1024 * 1024));
    }
}
