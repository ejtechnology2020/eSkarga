package pl.ejtechnology.tk.eSkarga.security;

import jakarta.enterprise.context.RequestScoped;

/** Żądanie z API workerów ({@code /api}, token z rolą {@code eskarga-worker}) – ustawiane przez {@code BearerTokenFilter}, czytane przez {@link RoleCheckInterceptor}. */
@RequestScoped
public class WorkerContext {

    private String subject;

    public void activate(String subject) {
        this.subject = subject;
    }

    public boolean isActive() {
        return subject != null;
    }

    public String getSubject() {
        return subject;
    }
}
