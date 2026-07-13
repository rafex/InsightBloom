package dev.rafex.insightbloom.users.bootstrap;

import dev.rafex.ether.http.jetty12.routing.JettyRouteRegistry;
import dev.rafex.ether.http.jetty12.JettyServerConfig;
import dev.rafex.ether.http.jetty12.JettyServerFactory;
import dev.rafex.ether.json.JacksonJsonCodec;
import dev.rafex.insightbloom.users.adapters.inbound.http.handlers.*;
import dev.rafex.insightbloom.users.adapters.outbound.cascade.HttpCascadeDeleteClient;
import dev.rafex.insightbloom.users.adapters.outbound.sqlite.*;
import dev.rafex.insightbloom.users.adapters.outbound.surveyclient.HttpSurveyClient;
import dev.rafex.insightbloom.users.adapters.outbound.telegramclient.HttpTelegramNotifyClient;
import dev.rafex.insightbloom.users.adapters.outbound.twilio.TwilioSmsClient;
import dev.rafex.insightbloom.users.adapters.outbound.zoho.ZohoEmailClient;
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
        final String etherpadApiKey = System.getenv().getOrDefault("ETHERPAD_API_KEY", "");
        final String jaasAppId = System.getenv().getOrDefault("JAAS_APP_ID", "");
        final String jaasApiKeyId = System.getenv().getOrDefault("JAAS_API_KEY_ID", "");
        final String jaasPrivateKeyBase64 = System.getenv().getOrDefault("JAAS_PRIVATE_KEY", "");
        final String llmBaseUrl = System.getenv().getOrDefault("LLM_PROVIDER_BASE_URL", "https://api.groq.com/openai/v1");
        final String llmApiKey = System.getenv().getOrDefault("LLM_PROVIDER_API_KEY", "");
        final String llmModel = System.getenv().getOrDefault("LLM_PROVIDER_MODEL", "openai/gpt-oss-120b");

        final String twilioAccountSid = System.getenv().getOrDefault("TWILIO_ACCOUNT_SID", "");
        final String twilioAuthToken = System.getenv().getOrDefault("TWILIO_AUTH_TOKEN", "");
        final String twilioFromNumber = System.getenv().getOrDefault("TWILIO_FROM_NUMBER", "");
        final String zohoSmtpHost = System.getenv().getOrDefault("ZOHO_SMTP_HOST", "smtp.zoho.com");
        final String zohoSmtpPort = System.getenv().getOrDefault("ZOHO_SMTP_PORT", "587");
        final String zohoUsername = System.getenv().getOrDefault("ZOHO_SMTP_USERNAME", "");
        final String zohoPassword = System.getenv().getOrDefault("ZOHO_SMTP_PASSWORD", "");
        final String zohoFromAddress = System.getenv().getOrDefault("ZOHO_FROM_ADDRESS", zohoUsername);

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
        final var platformSettingsRepo = new dev.rafex.insightbloom.users.adapters.outbound.sqlite.SqlitePlatformSettingsRepository(db);
        final var downloadEventRepo = new SqliteDownloadEventRepository(db);
        final var timezoneRepo = new SqliteTimezoneRepository(db);
        final var reservationRepo = new SqliteReservationRepository(db);
        final var venueSeatRepo = new SqliteVenueSeatRepository(db);
        final var eventTypeRepo = new SqliteEventTypeRepository(db);
        final var roleRepo = new SqliteRoleRepository(db);
        final var eventRoleRepo = new SqliteEventRoleRepository(db);
        final var sandboxRepo = new SqliteSandboxRepository(db);

        // Domain services
        final var tokenService = new TokenService(tokenRepo);
        final var passwordService = new PasswordService();
        final var friendlyIdService = new FriendlyIdService(conferenceRepo);
        final var cascadeDeletePort = new HttpCascadeDeleteClient(
                ingestUrl, queryUrl, moderationUrl, presentationsUrl, surveyUrl, internalApiKey);
        final var smsPort = new TwilioSmsClient(twilioAccountSid, twilioAuthToken, twilioFromNumber);
        final var emailPort = new ZohoEmailClient(zohoSmtpHost, zohoSmtpPort, zohoUsername, zohoPassword, zohoFromAddress);
        final var surveyPort = new HttpSurveyClient(surveyUrl);
        final var telegramNotifyPort = new HttpTelegramNotifyClient(telegramUrl, internalApiKey);
        final var etherpadPort = new dev.rafex.insightbloom.users.adapters.outbound.etherpadclient.HttpEtherpadPort(
                etherpadBaseUrl, etherpadApiKey);

        // Use cases
        final var loginUseCase = new LoginUseCase(userRepo, tokenService, passwordService);
        final var createGuestUseCase = new CreateGuestUseCase(guestRepo, conferenceRepo, tokenService);
        final var validateTokenUseCase = new ValidateTokenUseCase(tokenService, userRepo, guestRepo);
        final var createConferenceUseCase = new CreateConferenceUseCase(conferenceRepo, friendlyIdService, timezoneRepo, eventRoleRepo);
        final var getConferenceUseCase = new GetConferenceUseCase(conferenceRepo, cascadeDeletePort, membershipRepo);
        final var registerUseCase = new RegisterUseCase(userRepo, passwordService);
        final var sendOtpUseCase = new SendOtpUseCase(otpRepo, smsPort, emailPort);
        final var verifyOtpUseCase = new VerifyOtpUseCase(otpRepo, userRepo, tokenService);
        final var getUserProfileUseCase = new GetUserProfileUseCase(userRepo);
        final var updateProfileUseCase = new UpdateProfileUseCase(userRepo);
        final var changePasswordUseCase = new ChangePasswordUseCase(userRepo, passwordService);
        final var setSeatingModeUseCase = new SetSeatingModeUseCase(conferenceRepo, reservationRepo);
        final var reserveGeneralUseCase = new ReserveGeneralUseCase(conferenceRepo, reservationRepo);
        final var getMyTicketUseCase = new GetMyTicketUseCase(reservationRepo);
        final var cancelReservationUseCase = new CancelReservationUseCase(reservationRepo, conferenceRepo);
        final var listReservationsUseCase = new ListReservationsUseCase(conferenceRepo, reservationRepo);
        final var checkInTicketUseCase = new CheckInTicketUseCase(reservationRepo);
        final var setVenueMapUseCase = new SetVenueMapUseCase(conferenceRepo);
        final var defineVenueSeatsUseCase = new DefineVenueSeatsUseCase(conferenceRepo, venueSeatRepo, reservationRepo);
        final var getConferenceSeatMapUseCase = new GetConferenceSeatMapUseCase(venueSeatRepo, reservationRepo);
        final var reserveSeatUseCase = new ReserveSeatUseCase(
                conferenceRepo, reservationRepo, venueSeatRepo, userRepo, emailPort, frontendBaseUrl);
        final var joinConferenceUseCase = new JoinConferenceUseCase(
                getConferenceUseCase, membershipRepo, userRepo, emailPort, timezoneRepo,
                reserveGeneralUseCase, frontendBaseUrl);
        final var getConferenceHistoryUseCase = new GetConferenceHistoryUseCase(membershipRepo, conferenceRepo);
        final var generateCertificateUseCase = new GenerateCertificateUseCase(
                conferenceRepo, userRepo, surveyPort, certificateSettingsRepo);
        final var notifyDoubtAnsweredUseCase = new NotifyDoubtAnsweredUseCase(
                userRepo, conferenceRepo, emailPort, telegramNotifyPort, frontendBaseUrl, contactInfo);
        final var getCertificateSettingsUseCase = new GetCertificateSettingsUseCase(certificateSettingsRepo);
        final var saveCertificateSettingsUseCase = new SaveCertificateSettingsUseCase(certificateSettingsRepo);
        final var countAttendeesUseCase = new CountAttendeesUseCase(guestRepo);
        final var countRegisteredAttendeesUseCase = new CountRegisteredAttendeesUseCase(membershipRepo);
        final var countUniqueRegisteredAttendeesUseCase = new CountUniqueRegisteredAttendeesUseCase(conferenceRepo, membershipRepo);
        final var updateConferenceUseCase = new UpdateConferenceUseCase(conferenceRepo);
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
        final var eventPermissionGuard = new dev.rafex.insightbloom.users.domain.services.EventPermissionGuard(
                eventRoleRepo, roleRepo);
        final var assignEventRoleUseCase = new AssignEventRoleUseCase(eventRoleRepo, roleRepo, userRepo, eventPermissionGuard);
        final var listEventRolesUseCase = new ListEventRolesUseCase(eventRoleRepo, userRepo, eventPermissionGuard);
        final var removeEventRoleUseCase = new RemoveEventRoleUseCase(eventRoleRepo, conferenceRepo, eventPermissionGuard);
        final var sendConferenceRemindersUseCase = new SendConferenceRemindersUseCase(
                conferenceRepo, membershipRepo, userRepo, timezoneRepo, emailPort, reservationRepo, frontendBaseUrl);
        final var purgeExpiredEventNotesUseCase = new PurgeExpiredEventNotesUseCase(
                conferenceRepo, timezoneRepo, etherpadPort);
        final var eventCapabilityGuard = new dev.rafex.insightbloom.users.domain.services.EventCapabilityGuard(eventTypeRepo);
        final var setEventTypeUseCase = new SetEventTypeUseCase(conferenceRepo, eventTypeRepo, reservationRepo);
        final var getOrCreateEventPadUseCase = new GetOrCreateEventPadUseCase(conferenceRepo, etherpadPort);
        final var getEventDiagramUseCase = new GetEventDiagramUseCase(conferenceRepo);
        final var saveEventDiagramUseCase = new SaveEventDiagramUseCase(conferenceRepo);
        final var purgeExpiredEventDiagramsUseCase = new PurgeExpiredEventDiagramsUseCase(conferenceRepo, timezoneRepo);
        final var generateJaasTokenUseCase = new GenerateJaasTokenUseCase(
                jaasAppId, jaasApiKeyId, jaasPrivateKeyBase64, eventPermissionGuard, userRepo);
        final var llmClient = new dev.rafex.insightbloom.users.adapters.outbound.llm.GroqLlmClient(
                llmBaseUrl, llmApiKey, llmModel, JacksonJsonCodec.defaultCodec());
        final var generateSeatLayoutUseCase = new GenerateSeatLayoutUseCase(llmClient, JacksonJsonCodec.defaultCodec());
        final var getChatAiSettingUseCase = new GetChatAiSettingUseCase(platformSettingsRepo);
        final var setChatAiSettingUseCase = new SetChatAiSettingUseCase(platformSettingsRepo);
        final var setChatSettingsUseCase = new SetChatSettingsUseCase(platformSettingsRepo);
        final String gatewayBaseUrl = System.getenv().getOrDefault("GATEWAY_BASE_URL", "https://ide-insightbloom.v1.rafex.cloud");
        final var assignSandboxUseCase = new AssignSandboxUseCase(sandboxRepo, conferenceRepo);
        final var generateWorkspaceDownloadUrlUseCase = new GenerateWorkspaceDownloadUrlUseCase(sandboxRepo, gatewayBaseUrl);
        final var setSandboxInternetUseCase = new SetSandboxInternetUseCase(conferenceRepo);
        final var purgeSandboxPoolUseCase = new PurgeSandboxPoolUseCase(sandboxRepo);

        // Handlers
        final var authHandler = new AuthHandler(loginUseCase, createGuestUseCase, validateTokenUseCase,
                registerUseCase, sendOtpUseCase, verifyOtpUseCase, logoutUseCase, refreshTokenUseCase);
        final int maxPoolSizePerEvent = Integer.parseInt(System.getenv().getOrDefault("SANDBOX_POOL_MAX_PER_EVENT", "50"));
        final var setSandboxConfigUseCase = new SetSandboxConfigUseCase(conferenceRepo, maxPoolSizePerEvent);
        final var sandboxHandler = new SandboxHandler(
                assignSandboxUseCase, validateTokenUseCase, generateWorkspaceDownloadUrlUseCase,
                setSandboxConfigUseCase, gatewayBaseUrl);
        final var conferenceHandler = new ConferenceHandler(createConferenceUseCase, getConferenceUseCase,
                validateTokenUseCase, joinConferenceUseCase, getConferenceHistoryUseCase, generateCertificateUseCase,
                countAttendeesUseCase, countRegisteredAttendeesUseCase, countUniqueRegisteredAttendeesUseCase,
                updateConferenceUseCase,
                recordDownloadUseCase, getDownloadCountsUseCase,
                setSeatingModeUseCase, reserveGeneralUseCase, getMyTicketUseCase, cancelReservationUseCase,
                listReservationsUseCase, checkInTicketUseCase,
                setVenueMapUseCase, defineVenueSeatsUseCase, getConferenceSeatMapUseCase, reserveSeatUseCase,
                setEventTypeUseCase, eventCapabilityGuard, getOrCreateEventPadUseCase,
                assignEventRoleUseCase, listEventRolesUseCase, removeEventRoleUseCase,
                getEventDiagramUseCase, saveEventDiagramUseCase, generateJaasTokenUseCase, generateSeatLayoutUseCase,
                setSandboxConfigUseCase, sandboxHandler);
        final var userProfileHandler = new UserProfileHandler(getUserProfileUseCase, updateProfileUseCase,
                validateTokenUseCase, changePasswordUseCase);
        final var notifyHandler = new NotifyHandler(notifyDoubtAnsweredUseCase);
        final var certificateSettingsHandler = new CertificateSettingsHandler(
                getCertificateSettingsUseCase, saveCertificateSettingsUseCase, validateTokenUseCase);
        final var adminUserHandler = new AdminUserHandler(
                listUsersUseCase, adminUpdateUserUseCase, setUserStatusUseCase, validateTokenUseCase);
        final var timezoneHandler = new TimezoneHandler(listTimezonesUseCase);
        final var eventTypeHandler = new EventTypeHandler(listEventTypesUseCase, createEventTypeUseCase,
                updateEventTypeUseCase, setEventTypeActiveUseCase, validateTokenUseCase);
        final var eventCapabilityHandler = new EventCapabilityHandler();
        final var integrationConfigHandler = new IntegrationConfigHandler(
                drawioBaseUrl, etherpadBaseUrl, jaasAppId, excalidrawBaseUrl);
        final var roleHandler = new RoleHandler(listRolesUseCase, createRoleUseCase, updateRoleUseCase,
                setRoleActiveUseCase, validateTokenUseCase);
        final var permissionHandler = new PermissionHandler();
        final var platformSettingsHandler = new PlatformSettingsHandler(
                getChatAiSettingUseCase, setChatAiSettingUseCase, setChatSettingsUseCase, validateTokenUseCase);

        // Route registry
        final var routes = new JettyRouteRegistry();
        routes.add("/api/v1/auth/*", authHandler);
        routes.add("/api/v1/conferences/*", conferenceHandler);
        routes.add("/api/v1/users/*", userProfileHandler);
        routes.add("/api/v1/notify/*", notifyHandler);
        routes.add("/api/v1/certificate-settings/*", certificateSettingsHandler);
        routes.add("/api/v1/admin/users/*", adminUserHandler);
        routes.add("/api/v1/timezones/*", timezoneHandler);
        routes.add("/api/v1/event-types/*", eventTypeHandler);
        routes.add("/api/v1/event-capabilities/*", eventCapabilityHandler);
        routes.add("/api/v1/integrations/*", integrationConfigHandler);
        routes.add("/api/v1/roles/*", roleHandler);
        routes.add("/api/v1/permissions/*", permissionHandler);
        routes.add("/api/v1/settings/*", platformSettingsHandler);

        // Server
        final var codec = JacksonJsonCodec.defaultCodec();
        final var config = JettyServerConfig.fromEnv();
        final var runner = JettyServerFactory.create(config, routes, codec, null, java.util.List.of(), java.util.List.of());

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
        }, 1, 5, java.util.concurrent.TimeUnit.MINUTES);

        runner.start();
        runner.await();
    }
}
