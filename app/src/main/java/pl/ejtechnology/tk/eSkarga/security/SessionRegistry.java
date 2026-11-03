package pl.ejtechnology.tk.eSkarga.security;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.servlet.http.HttpSession;

/** Sesje HTTP według identyfikatora sesji dostawcy ({@code sid}) i podmiotu ({@code sub}) – do back-channel logout. */
@ApplicationScoped
public class SessionRegistry {

    public static final String SID_ATTR = "eskarga.oidc.sid";
    public static final String SUB_ATTR = "eskarga.oidc.sub";

    private final Map<String, HttpSession> bySid = new ConcurrentHashMap<>();
    private final Map<String, List<HttpSession>> bySub = new ConcurrentHashMap<>();

    public void register(HttpSession session, String sub, String sid) {
        if (sid != null) {
            session.setAttribute(SID_ATTR, sid);
            bySid.put(sid, session);
        }
        if (sub != null) {
            session.setAttribute(SUB_ATTR, sub);
            bySub.computeIfAbsent(sub, k -> new ArrayList<>()).add(session);
        }
    }

    public void unregister(HttpSession session) {
        Object sid = session.getAttribute(SID_ATTR);
        if (sid != null) {
            bySid.remove(sid.toString());
        }
        Object sub = session.getAttribute(SUB_ATTR);
        if (sub != null) {
            bySub.computeIfPresent(sub.toString(), (k, list) -> {
                list.remove(session);
                return list.isEmpty() ? null : list;
            });
        }
    }

    /** Unieważnia sesje wskazane przez {@code sid} (jedna) albo {@code sub} (wszystkie); zwraca liczbę unieważnionych. */
    public int invalidate(String sid, String sub) {
        List<HttpSession> targets = new ArrayList<>();
        if (sid != null && bySid.containsKey(sid)) {
            targets.add(bySid.get(sid));
        } else if (sub != null && bySub.containsKey(sub)) {
            targets.addAll(bySub.get(sub));
        }
        int n = 0;
        for (HttpSession s : targets) {
            try {
                s.invalidate();
                n++;
            } catch (IllegalStateException alreadyInvalid) {
                unregister(s);
            }
        }
        return n;
    }

    public int size() {
        return bySid.size();
    }
}
