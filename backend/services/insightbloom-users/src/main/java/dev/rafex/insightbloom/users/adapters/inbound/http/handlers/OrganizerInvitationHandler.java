package dev.rafex.insightbloom.users.adapters.inbound.http.handlers;

import dev.rafex.ether.http.jetty12.exchange.JettyHttpExchange;
import dev.rafex.ether.http.core.Route;
import dev.rafex.ether.http.core.HttpExchange;
import dev.rafex.insightbloom.users.application.usecases.OrganizerInvitationUseCase;
import dev.rafex.insightbloom.users.domain.model.OrganizerInviteSettings;
import dev.rafex.insightbloom.users.application.usecases.ValidateTokenUseCase;
import dev.rafex.insightbloom.common.http.BaseResourceHandler;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** API for platform-admin invitations and authenticated organizer referrals. */
public final class OrganizerInvitationHandler extends BaseResourceHandler {
    private final OrganizerInvitationUseCase useCase;
    private final ValidateTokenUseCase validateTokenUseCase;

    public OrganizerInvitationHandler(final OrganizerInvitationUseCase useCase,
            final ValidateTokenUseCase validateTokenUseCase) {
        this.useCase = useCase; this.validateTokenUseCase = validateTokenUseCase;
    }

    @Override protected String basePath() { return "/api/v1"; }
    @Override protected List<Route> routes() {
        return List.of(
            Route.of("/admin/organizer-invitations", Set.of("GET","POST")),
            Route.of("/admin/organizer-invitations/settings", Set.of("GET","PUT")),
            Route.of("/admin/organizer-invitations/referrals", Set.of("GET")),
            Route.of("/admin/organizer-invitations/referrals/{uuid}/approve", Set.of("POST")),
            Route.of("/admin/organizer-invitations/referrals/{uuid}/reject", Set.of("POST")),
            Route.of("/admin/organizer-invitations/referrers", Set.of("GET")),
            Route.of("/admin/organizer-invitations/referrers/eligible", Set.of("GET")),
            Route.of("/admin/organizer-invitations/referrers/{userUuid}", Set.of("PUT")),
            Route.of("/admin/organizer-invitations/{uuid}/resend", Set.of("POST")),
            Route.of("/admin/organizer-invitations/{uuid}/revoke", Set.of("POST")),
            Route.of("/organizer-invitations/preview", Set.of("POST")),
            Route.of("/organizer-invitations/redeem", Set.of("POST")),
            Route.of("/organizer-invitations/referrals", Set.of("GET","POST")));
    }
    @Override public Set<String> supportedMethods() { return Set.of("GET","POST","PUT"); }

    @Override public boolean get(final HttpExchange x) {
        final var jx=asJetty(x); final String path=jx.path();
        if(path.contains("/admin/organizer-invitations")) {
            if(!requireAdmin(jx)) return true;
            if(path.endsWith("/settings")) { sendOk(jx,useCase.settings()); return true; }
            if(path.endsWith("/referrers/eligible")) { sendOk(jx,Map.of("items",useCase.listReferralEligibleOrganizers())); return true; }
            if(path.endsWith("/referrers")) { sendOk(jx,Map.of("items",useCase.listEnabledReferrers())); return true; }
            if(path.endsWith("/referrals")) { sendOk(jx,Map.of("items",useCase.listReferrals(queryParam(jx,"status"),100,0))); return true; }
            sendOk(jx,Map.of("items",useCase.listInvitations(queryParam(jx,"status"),100,0))); return true;
        }
        if(path.endsWith("/organizer-invitations/referrals")) {
            final var auth=requireUser(jx); if(auth==null)return true;
            sendOk(jx,useCase.myReferrals(auth.subjectUuid())); return true;
        }
        return false;
    }

    @Override public boolean post(final HttpExchange x) {
        final var jx=asJetty(x); final String path=jx.path();
        try {
            final Map<String,Object> body=parseBody(jx);
            if(path.endsWith("/organizer-invitations/preview")) {
                useCase.recordPublicAttempt("INVITE_PREVIEW",clientIpHash(jx));
                final var preview=useCase.preview(string(body,"token"));
                if(preview.isEmpty()) sendOk(jx,Map.of("valid",false));
                else sendOk(jx,Map.of("valid",true,"emailHint",preview.get().emailHint(),"expiresAt",preview.get().expiresAt().toString()));
                return true;
            }
            if(path.endsWith("/organizer-invitations/redeem")) {
                useCase.recordPublicAttempt("INVITE_REDEEM",clientIpHash(jx));
                final var auth=requireUser(jx); if(auth==null)return true;
                final var result=useCase.redeem(string(body,"token"),auth.subjectUuid());
                sendOk(jx,Map.of("token",result.token(),"userUuid",result.userUuid(),"role",result.role(),"expiresAt",result.expiresAt()));
                return true;
            }
            if(path.endsWith("/organizer-invitations/referrals")) {
                final var auth=requireUser(jx); if(auth==null)return true;
                sendOk(jx,201,useCase.submitReferral(auth.subjectUuid(),string(body,"email"),clientIpHash(jx)));
                return true;
            }
            if(!path.contains("/admin/organizer-invitations") || !requireAdmin(jx)) return path.contains("/admin/organizer-invitations");
            if(path.endsWith("/referrals/approve")) {
                final var row=useCase.approveReferral(jx.pathParam("uuid"),adminSubject(jx),clientIpHash(jx));sendOk(jx,201,row);return true;
            }
            if(path.endsWith("/referrals/reject")) {
                useCase.rejectReferral(jx.pathParam("uuid"),adminSubject(jx));sendOk(jx,Map.of("status","REJECTED"));return true;
            }
            if(path.endsWith("/resend")) {sendOk(jx,useCase.resendInvitation(jx.pathParam("uuid"),adminSubject(jx),clientIpHash(jx)));return true;}
            if(path.endsWith("/revoke")) {useCase.revokeInvitation(jx.pathParam("uuid"),adminSubject(jx));sendOk(jx,Map.of("status","REVOKED"));return true;}
            sendOk(jx,201,useCase.createInvitation(string(body,"email"),adminSubject(jx),clientIpHash(jx)));return true;
        } catch(IllegalArgumentException e) { return handleClientError(jx,e); }
        catch(Exception e) { sendError(jx,500,"internal_error","Internal server error");return true; }
    }

    @Override public boolean put(final HttpExchange x) {
        final var jx=asJetty(x); if(!requireAdmin(jx))return true;
        try {
            final Map<String,Object> body=parseBody(jx);final String path=jx.path();
            if(path.endsWith("/settings")) {
                final var s=useCase.updateSettings(bool(body,"referralsEnabled"),integer(body,"invitationTtlDays"),integer(body,"referralLimit"),adminSubject(jx));
                sendOk(jx,s);return true;
            }
            if(path.contains("/referrers/")) {
                useCase.setReferrerEnabled(jx.pathParam("userUuid"),bool(body,"enabled"),adminSubject(jx));
                sendOk(jx,Map.of("userUuid",jx.pathParam("userUuid"),"enabled",bool(body,"enabled")));return true;
            }
            sendError(jx,404,"not_found","Endpoint not found");return true;
        }catch(IllegalArgumentException e){return handleClientError(jx,e);}
        catch(Exception e){sendError(jx,500,"internal_error","Internal server error");return true;}
    }

    private boolean requireAdmin(final JettyHttpExchange jx) {
        final var auth=requireUser(jx); if(auth==null)return false;
        if(!hasRole(auth.role(),"ADMIN")){sendError(jx,403,"forbidden","Only platform admins can manage invitations");return false;}
        return true;
    }
    private String adminSubject(final JettyHttpExchange jx) {
        final var auth=validate(extractToken(jx));return auth==null?"":auth.subjectUuid();
    }
    private ValidateTokenUseCase.ValidationResult requireUser(final JettyHttpExchange jx) {
        final var auth=validate(extractToken(jx));
        if(auth==null||!"user".equals(auth.kind())){sendError(jx,401,"token_invalid","Authentication required");return null;}
        return auth;
    }
    private ValidateTokenUseCase.ValidationResult validate(final String token) {
        if(token==null)return null;final var result=validateTokenUseCase.execute(token);return result.valid()?result:null;
    }
    private static String extractToken(final JettyHttpExchange jx) {
        final String value=jx.request().getHeaders().get("Authorization");
        return value!=null&&value.startsWith("Bearer ")?value.substring(7):null;
    }
    static boolean hasRole(final String csv,final String role) {
        if(csv==null)return false;for(String part:csv.split(","))if(role.equalsIgnoreCase(part.trim()))return true;return false;
    }
    private boolean handleClientError(final JettyHttpExchange jx,final IllegalArgumentException e) {
        final String code=e.getMessage()==null?"bad_request":e.getMessage();
        final int status=code.contains("rate_limit")?429:code.contains("exists")||code.contains("pending")||code.contains("quota")?409:code.contains("organizer")||code.contains("referrals_not_enabled")?403:400;
        final String message=code.equals("invitation_invalid_or_expired")?"La invitación no es válida o ya venció.":"No se pudo completar la operación. Revisa los datos o el estado e inténtalo nuevamente.";
        sendError(jx,status,code,message);return true;
    }
    private static String string(final Map<String,Object> body,final String key){final Object v=body.get(key);return v instanceof String s?s:null;}
    private static boolean bool(final Map<String,Object> body,final String key){return Boolean.TRUE.equals(body.get(key));}
    private static int integer(final Map<String,Object> body,final String key){final Object v=body.get(key);return v instanceof Number n?n.intValue():-1;}
    private static String clientIpHash(final JettyHttpExchange jx) {
        final var address=OtpClientMetadata.lastForwardedAddress(jx.request().getHeaders().get("X-Forwarded-For"));
        if(address.isEmpty())return null;
        try{return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(address.get().getBytes(StandardCharsets.UTF_8)));}
        catch(Exception e){return null;}
    }
}
