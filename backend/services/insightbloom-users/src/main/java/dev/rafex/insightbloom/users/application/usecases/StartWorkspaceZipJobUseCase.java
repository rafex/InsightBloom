package dev.rafex.insightbloom.users.application.usecases;

import dev.rafex.insightbloom.users.domain.model.Conference;
import dev.rafex.insightbloom.users.domain.model.Sandbox;
import dev.rafex.insightbloom.users.domain.model.User;
import dev.rafex.insightbloom.users.domain.model.WorkspaceZipJob;
import dev.rafex.insightbloom.users.domain.ports.ConferenceRepository;
import dev.rafex.insightbloom.users.domain.ports.EmailPort;
import dev.rafex.insightbloom.users.domain.ports.SandboxOrchestrator;
import dev.rafex.insightbloom.users.domain.ports.SandboxRepository;
import dev.rafex.insightbloom.users.domain.ports.UserRepository;
import dev.rafex.insightbloom.users.domain.ports.WorkspaceZipJobRepository;
import dev.rafex.insightbloom.users.domain.services.WorkspaceZipCache;
import dev.rafex.insightbloom.users.domain.services.WorkspaceZipReadyEmailTemplate;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Arranca la construcción del zip del workspace en background -- reemplaza el flujo síncrono que
 * hacía esperar al navegador la compresión completa (el pod permite hasta 90s, ver
 * KubernetesPodClient.downloadWorkspaceZip). execute() solo crea la fila PENDING y devuelve de
 * inmediato; el trabajo real corre en {@code backgroundExecutor} sin el límite de tiempo de un
 * request HTTP, y al terminar notifica in-app + manda email (SendNotificationUseCase,
 * WorkspaceZipReadyEmailTemplate) -- ambos best-effort, un fallo ahí no tira abajo el job.
 */
public class StartWorkspaceZipJobUseCase {
    private static final Logger LOGGER = Logger.getLogger(StartWorkspaceZipJobUseCase.class.getName());
    private static final SecureRandom RANDOM = new SecureRandom();
    static final long TTL_SECONDS = 2 * 60 * 60;

    private final SandboxRepository sandboxRepository;
    private final ConferenceRepository conferenceRepository;
    private final UserRepository userRepository;
    private final WorkspaceZipJobRepository jobRepository;
    private final WorkspaceZipCache zipCache;
    private final SandboxOrchestrator sandboxOrchestrator;
    private final SendNotificationUseCase sendNotificationUseCase;
    private final EmailPort emailPort;
    private final ExecutorService backgroundExecutor;
    private final String downloadBaseUrl;

    public StartWorkspaceZipJobUseCase(final SandboxRepository sandboxRepository,
                                        final ConferenceRepository conferenceRepository,
                                        final UserRepository userRepository,
                                        final WorkspaceZipJobRepository jobRepository,
                                        final WorkspaceZipCache zipCache,
                                        final SandboxOrchestrator sandboxOrchestrator,
                                        final SendNotificationUseCase sendNotificationUseCase,
                                        final EmailPort emailPort,
                                        final ExecutorService backgroundExecutor,
                                        final String downloadBaseUrl) {
        this.sandboxRepository = sandboxRepository;
        this.conferenceRepository = conferenceRepository;
        this.userRepository = userRepository;
        this.jobRepository = jobRepository;
        this.zipCache = zipCache;
        this.sandboxOrchestrator = sandboxOrchestrator;
        this.sendNotificationUseCase = sendNotificationUseCase;
        this.emailPort = emailPort;
        this.backgroundExecutor = backgroundExecutor;
        this.downloadBaseUrl = downloadBaseUrl;
    }

    public WorkspaceZipJob execute(final String conferenceUuid, final String userUuid) {
        final Sandbox sandbox = sandboxRepository.findByConferenceAndUser(conferenceUuid, userUuid)
                .orElseThrow(() -> new IllegalArgumentException("sandbox_not_assigned"));
        final WorkspaceZipJob job = new WorkspaceZipJob(
                UUID.randomUUID().toString(), conferenceUuid, sandbox.getUuid(), userUuid,
                WorkspaceZipJob.STATUS_PENDING, null, Instant.now(), null, null, null);
        jobRepository.save(job);
        backgroundExecutor.submit(() -> buildZip(job, sandbox));
        return job;
    }

    private void buildZip(final WorkspaceZipJob job, final Sandbox sandbox) {
        try {
            final byte[] zip = sandboxOrchestrator.downloadWorkspaceZip(sandbox.podName(), sandbox.getSeatIndex());
            zipCache.put(job.uuid(), zip);
            final Instant readyAt = Instant.now();
            final WorkspaceZipJob ready = new WorkspaceZipJob(
                    job.uuid(), job.conferenceUuid(), job.sandboxUuid(), job.userUuid(),
                    WorkspaceZipJob.STATUS_READY, generateToken(), job.createdAt(), readyAt,
                    readyAt.plusSeconds(TTL_SECONDS), null);
            jobRepository.save(ready);
            notifyReady(ready);
        } catch (final Exception e) {
            LOGGER.log(Level.WARNING, "workspace-zip-job: fallo al armar el zip de " + job.uuid(), e);
            final WorkspaceZipJob failed = new WorkspaceZipJob(
                    job.uuid(), job.conferenceUuid(), job.sandboxUuid(), job.userUuid(),
                    WorkspaceZipJob.STATUS_FAILED, null, job.createdAt(), null, null,
                    e.getMessage() != null ? e.getMessage() : "workspace_zip_failed");
            jobRepository.save(failed);
        }
    }

    private void notifyReady(final WorkspaceZipJob job) {
        final Conference conference = conferenceRepository.findByUuid(job.conferenceUuid()).orElse(null);
        final String conferenceName = conference != null ? conference.getName() : "tu evento";
        final String downloadUrl = downloadBaseUrl + "/workspaces/" + job.uuid() + "/download?token=" + job.downloadToken();
        final String inAppLink = conference != null ? "/c/" + conference.getFriendlyId() + "/ide" : null;

        try {
            sendNotificationUseCase.execute(job.userUuid(), "workspace_zip_ready",
                    "Tu workspace está listo para descargar",
                    "El ZIP de " + conferenceName + " ya se generó. Tenés 2 horas para descargarlo.",
                    inAppLink);
        } catch (final Exception e) {
            LOGGER.log(Level.WARNING, "workspace-zip-job: no se pudo notificar in-app a " + job.userUuid(), e);
        }

        try {
            if (emailPort.isEnabled()) {
                final User user = userRepository.findByUuid(job.userUuid()).orElse(null);
                if (user != null && user.getEmail() != null && !user.getEmail().isBlank()) {
                    final String html = WorkspaceZipReadyEmailTemplate.render(conferenceName, downloadUrl);
                    emailPort.sendHtml(user.getEmail(), "Tu workspace está listo para descargar", html);
                }
            }
        } catch (final Exception e) {
            LOGGER.log(Level.WARNING, "workspace-zip-job: no se pudo enviar el email a " + job.userUuid(), e);
        }
    }

    private static String generateToken() {
        final byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
