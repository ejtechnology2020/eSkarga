package pl.ejtechnology.tk.eSkarga.security;

import java.io.IOException;
import java.util.Optional;

import jakarta.inject.Inject;
import jakarta.json.JsonObject;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import pl.ejtechnology.tk.eSkarga.api.JwtRs256Verifier;
import pl.ejtechnology.tk.eSkarga.audit.AuditService;

/**
 * Back-channel logout z ID-users (T-004): {@code POST logout_token=...} → weryfikacja JWT kluczem wydawcy →
 * unieważnienie sesji po {@code sid}/{@code sub}. Odpowiedzi zgodne ze specyfikacją: 200 OK, 400 Bad Request.
 */
@WebServlet(urlPatterns = "/oidc/backchannel-logout", name = "backChannelLogout")
public class BackChannelLogoutServlet extends HttpServlet {

    @Inject
    SessionRegistry sessions;
    @Inject
    AuditService audit;
    @Inject
    OidcSettings settings;

    private transient JwtRs256Verifier verifier;

    JwtRs256Verifier verifier() {
        if (verifier == null) {
            verifier = JwtRs256Verifier.forClient();
        }
        return verifier;
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setHeader("Cache-Control", "no-store");
        if (settings.isDemo()) {
            resp.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        String token = req.getParameter("logout_token");
        if (token == null || token.isBlank()) {
            resp.sendError(HttpServletResponse.SC_BAD_REQUEST);
            return;
        }
        Optional<JsonObject> payload = verifier().verifyPayload(token);
        Optional<LogoutToken.Target> target = payload.flatMap(LogoutToken::target);
        if (target.isEmpty()) {
            resp.sendError(HttpServletResponse.SC_BAD_REQUEST);
            return;
        }
        int n = sessions.invalidate(target.get().sid(), target.get().sub());
        audit.record(target.get().sub() == null ? "sid:" + target.get().sid() : target.get().sub(), null, "wylogowanie back-channel z ID-users (sesje: " + n + ")", null, null, req.getRemoteAddr());
        resp.setStatus(HttpServletResponse.SC_OK);
    }
}
