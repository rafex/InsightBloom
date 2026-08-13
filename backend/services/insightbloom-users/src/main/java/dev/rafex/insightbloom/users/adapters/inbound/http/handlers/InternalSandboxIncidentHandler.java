package dev.rafex.insightbloom.users.adapters.inbound.http.handlers;

import dev.rafex.ether.http.core.HttpExchange;
import dev.rafex.ether.http.core.Route;
import dev.rafex.insightbloom.common.http.BaseResourceHandler;
import dev.rafex.insightbloom.users.application.usecases.RecordSandboxIncidentUseCase;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Llamado por el seat-agent de un Pod "neovim" compartido (ver {@code sandbox-agent.py}, funcion
 * {@code _report_incident}) cuando el watchdog termina un asiento por abuso de recursos.
 *
 * Autenticado con {@code SANDBOX_INCIDENT_REPORT_KEY} via header {@code X-Sandbox-Incident-Key}
 * -- DELIBERADAMENTE NO usa {@code BaseResourceHandler.validInternalAuth}/{@code
 * INTERNAL_API_KEY} (el secreto compartido de plataforma que protege TODOS los demas endpoints
 * {@code /internal/*}): ese secreto vive dentro de contenedores que un alumno puede leer via
 * "env" en su propio sandbox, así que filtrarlo aca seria darle a cualquier alumno la llave de
 * TODA la superficie interna de la plataforma. Este secreto es de proposito unico y bajo
 * privilegio -- lo peor que permite, si se filtra, es escribir incidentes falsos. Ver DEC-0025.
 */
public class InternalSandboxIncidentHandler extends BaseResourceHandler {
    private final RecordSandboxIncidentUseCase recordSandboxIncidentUseCase;
    private final String incidentReportKey;

    public InternalSandboxIncidentHandler(final RecordSandboxIncidentUseCase recordSandboxIncidentUseCase,
                                           final String incidentReportKey) {
        this.recordSandboxIncidentUseCase = recordSandboxIncidentUseCase;
        this.incidentReportKey = incidentReportKey;
    }

    @Override
    protected String basePath() {
        return "/internal/sandbox-incidents";
    }

    @Override
    protected List<Route> routes() {
        return List.of(Route.of("/", Set.of("POST")));
    }

    @Override
    public Set<String> supportedMethods() {
        return Set.of("POST");
    }

    @Override
    public boolean post(final HttpExchange x) {
        final var jx = asJetty(x);
        final String header = jx.request().getHeaders().get("X-Sandbox-Incident-Key");
        if (incidentReportKey == null || incidentReportKey.isEmpty() || !incidentReportKey.equals(header)) {
            sendError(jx, 403, "forbidden", "Internal endpoint");
            return true;
        }
        try {
            final var body = parseBody(jx);
            final String conferenceUuid = (String) body.get("conferenceUuid");
            final String podName = (String) body.get("podName");
            final Integer seatIndex = (Integer) body.get("seatIndex");
            final String userUuid = (String) body.get("userUuid");
            final String type = (String) body.get("type");
            final String detail = (String) body.get("detail");
            final var incident = recordSandboxIncidentUseCase.execute(
                    conferenceUuid, podName, seatIndex != null ? seatIndex : 0, userUuid, type, detail);
            sendOk(jx, 201, Map.of("uuid", incident.getUuid()));
        } catch (final IllegalArgumentException e) {
            sendError(jx, 400, e.getMessage(), e.getMessage());
        } catch (final Exception e) {
            sendInternalError(jx, e);
        }
        return true;
    }
}
