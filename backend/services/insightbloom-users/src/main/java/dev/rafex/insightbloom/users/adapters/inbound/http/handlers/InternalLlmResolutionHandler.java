package dev.rafex.insightbloom.users.adapters.inbound.http.handlers;

import dev.rafex.ether.http.core.*;
import dev.rafex.insightbloom.common.http.BaseResourceHandler;
import dev.rafex.insightbloom.users.application.usecases.LlmConfigurationUseCase;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/** Read-only current event authority; no credentials or gateway calls. */
public final class InternalLlmResolutionHandler extends BaseResourceHandler {
  private final LlmConfigurationUseCase llm;
  private final String token;

  public InternalLlmResolutionHandler(LlmConfigurationUseCase llm, String token) {
    this.llm = llm;
    this.token = token == null ? "" : token;
  }

  protected String basePath() {
    return "/internal/llm-resolution";
  }

  protected List<Route> routes() {
    return List.of(Route.of("/{eventId}/{capability}", Set.of("GET")));
  }

  public Set<String> supportedMethods() {
    return Set.of("GET");
  }

  public boolean get(HttpExchange x) {
    var jx = asJetty(x);
    String supplied = jx.request().getHeaders().get("X-Service-Token");
    if (token.isBlank()
        || supplied == null
        || !MessageDigest.isEqual(
            token.getBytes(StandardCharsets.UTF_8), supplied.getBytes(StandardCharsets.UTF_8))) {
      sendError(jx, 403, "forbidden", "Internal endpoint");
      return true;
    }
    try {
      sendOk(jx, llm.context(jx.pathParam("eventId"), jx.pathParam("capability")));
    } catch (IllegalArgumentException e) {
      sendError(jx, 404, "event_not_found", "Event or capability not found");
    } catch (Exception e) {
      sendError(jx, 503, "resolution_unavailable", "Resolution unavailable");
    }
    return true;
  }
}
