package dev.rafex.insightbloom.users.adapters.inbound.http.handlers;

import dev.rafex.ether.http.core.HttpExchange;
import dev.rafex.ether.http.core.Route;
import dev.rafex.ether.http.jetty12.exchange.JettyHttpExchange;
import dev.rafex.insightbloom.common.http.BaseResourceHandler;
import dev.rafex.insightbloom.users.application.usecases.DownloadWorkspaceZipUseCase;
import org.eclipse.jetty.http.HttpHeader;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.Set;

/**
 * Descarga publica del workspace del alumno -- {@code token} self-contained en el query param
 * (ver GenerateWorkspaceDownloadUrlUseCase/WorkspaceDownloadToken), sin Authorization Bearer: el
 * link se abre con {@code window.location.href} directo desde el navegador (ver IdePage.vue),
 * no via fetch/axios con headers.
 */
public class WorkspaceDownloadHandler extends BaseResourceHandler {
    private final DownloadWorkspaceZipUseCase downloadWorkspaceZipUseCase;

    public WorkspaceDownloadHandler(final DownloadWorkspaceZipUseCase downloadWorkspaceZipUseCase) {
        this.downloadWorkspaceZipUseCase = downloadWorkspaceZipUseCase;
    }

    @Override
    public String basePath() {
        return "/workspaces";
    }

    @Override
    public List<Route> routes() {
        return List.of(Route.of("/{uuid}/download", Set.of("GET")));
    }

    @Override
    public Set<String> supportedMethods() {
        return Set.of("GET");
    }

    @Override
    public boolean get(final HttpExchange x) {
        final var jx = asJetty(x);
        final String sandboxUuid = jx.pathParam("uuid");
        final String token = queryParam(jx, "token");
        try {
            final byte[] zip = downloadWorkspaceZipUseCase.execute(sandboxUuid, token);
            sendZip(jx, zip);
        } catch (final IllegalArgumentException e) {
            sendError(jx, mapErrorStatus(e.getMessage()), e.getMessage(), e.getMessage());
        } catch (final Exception e) {
            sendError(jx, 500, "internal_error", e.getMessage());
        }
        return true;
    }

    private void sendZip(final JettyHttpExchange jx, final byte[] body) {
        jx.response().setStatus(200);
        jx.response().getHeaders().put(HttpHeader.CONTENT_TYPE, "application/zip");
        jx.response().getHeaders().put(HttpHeader.CONTENT_DISPOSITION, "attachment; filename=\"workspace.zip\"");
        jx.response().getHeaders().put(HttpHeader.CONTENT_LENGTH, Long.toString(body.length));
        jx.response().write(true, ByteBuffer.wrap(body), jx.callback());
    }

    private static int mapErrorStatus(final String message) {
        if ("token_invalid".equals(message)) return 401;
        if ("sandbox_not_found".equals(message)) return 404;
        if ("workspace_not_found".equals(message)) return 404;
        if ("workspace_too_large".equals(message)) return 413;
        return 400;
    }
}
