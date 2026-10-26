package dev.rafex.insightbloom.users.adapters.inbound.http.handlers;

import dev.rafex.ether.http.core.*;
import dev.rafex.insightbloom.common.http.BaseResourceHandler;
import dev.rafex.insightbloom.users.application.usecases.*;
import java.util.*;

public final class PersonalLlmHandler extends BaseResourceHandler {
  private final LlmConfigurationUseCase llm;
  private final ValidateTokenUseCase tokens;

  public PersonalLlmHandler(LlmConfigurationUseCase llm, ValidateTokenUseCase tokens) {
    this.llm = llm;
    this.tokens = tokens;
  }

  protected String basePath() {
    return "/api/v1/me/llm";
  }

  protected List<Route> routes() {
    return List.of(
        Route.of("/events", Set.of("GET")),
        Route.of("/events/{eventId}/{capability}", Set.of("DELETE")),
        Route.of("/{capability}", Set.of("GET", "PUT", "DELETE")));
  }

  public Set<String> supportedMethods() {
    return Set.of("GET", "PUT", "DELETE");
  }

  public boolean get(HttpExchange x) {
    return handle(x, "GET");
  }

  public boolean put(HttpExchange x) {
    return handle(x, "PUT");
  }

  public boolean delete(HttpExchange x) {
    return handle(x, "DELETE");
  }

  private boolean handle(HttpExchange x, String method) {
    var jx = asJetty(x);
    String header = jx.request().getHeaders().get("Authorization");
    String token = header != null && header.startsWith("Bearer ") ? header.substring(7) : null;
    if (token == null) {
      sendError(jx, 401, "token_missing", "Inicia sesión");
      return true;
    }
    var actor = tokens.execute(token);
    if (!actor.valid() || !"user".equals(actor.kind())) {
      sendError(jx, 403, "forbidden", "Acceso no autorizado");
      return true;
    }
    try {
      String cap = jx.pathParam("capability"), event = jx.pathParam("eventId");
      if (event != null) {
        llm.withdraw(actor.subjectUuid(), event, cap);
        sendOk(jx, Map.of("removed", true));
      } else if (jx.path().endsWith("/events"))
        sendOk(jx, Map.of("events", llm.dependencies(actor.subjectUuid())));
      else if (method.equals("GET")) sendOk(jx, llm.personalStatus(actor.subjectUuid(), cap));
      else if (method.equals("PUT"))
        sendOk(jx, llm.savePersonal(actor.subjectUuid(), cap, parseBody(jx)));
      else {
        llm.clearPersonal(actor.subjectUuid(), cap);
        sendOk(jx, Map.of("configured", false));
      }
    } catch (SecurityException e) {
      sendError(jx, 403, "forbidden", "Acceso no autorizado");
    } catch (IllegalArgumentException e) {
      sendError(
          jx,
          400,
          "llm_input_invalid",
          "Revisa la configuración; un proveedor o URL nuevos requieren una clave nueva");
    } catch (Exception e) {
      sendError(jx, 503, "llm_gateway_unavailable", "No se pudo completar la operación IA");
    }
    return true;
  }
}
