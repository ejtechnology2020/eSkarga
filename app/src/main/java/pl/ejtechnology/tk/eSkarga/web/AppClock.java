package pl.ejtechnology.tk.eSkarga.web;

import java.io.Serializable;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Named;

/**
 * Zegar aplikacji. Strefa Europe/Warsaw tylko przy prezentacji i liczeniu terminów (CLAUDE.md).
 * W trybie danych przykładowych „dziś” to 03.11.2026 (property {@code eskarga.demo.today}), żeby ekrany
 * były spójne z briefem; w trybie OIDC zegar jest zawsze systemowy.
 */
@Named("clock")
@ApplicationScoped
public class AppClock implements Serializable {

    public static final ZoneId WARSAW = ZoneId.of("Europe/Warsaw");

    private final Clock clock;

    public AppClock() {
        String fixed = System.getProperty("eskarga.demo.today", System.getenv("ESKARGA_DEMO_TODAY"));
        // zatrzymany zegar tylko w trybie demo (ADR 0004); w trybie OIDC terminy i „czeka od” liczy zegar systemowy,
        // nawet gdy .env dev ma ESKARGA_DEMO_TODAY (inaczej zadanie z dziś miało „3 dni oczekiwania”)
        boolean demo = pl.ejtechnology.tk.eSkarga.security.OidcSettings.MODE_DEMO
                .equalsIgnoreCase(pl.ejtechnology.tk.eSkarga.security.OidcSettings.value("eskarga.security.mode", pl.ejtechnology.tk.eSkarga.security.OidcSettings.MODE_DEMO));
        if (demo && fixed != null && !fixed.isBlank()) {
            LocalDateTime ldt = LocalDate.parse(fixed).atTime(9, 0);
            this.clock = Clock.fixed(ldt.atZone(WARSAW).toInstant(), WARSAW);
        } else {
            this.clock = Clock.system(WARSAW);
        }
    }

    public AppClock(Clock clock) {
        this.clock = clock;
    }

    public LocalDate today() {
        return LocalDate.now(clock);
    }

    public LocalDateTime now() {
        return LocalDateTime.now(clock);
    }

    public Instant instant() {
        return clock.instant();
    }

    public Clock clock() {
        return clock;
    }

    public ZoneOffset offset() {
        return WARSAW.getRules().getOffset(clock.instant());
    }

    public LocalDate getToday() {
        return today();
    }
}
