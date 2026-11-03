package pl.ejtechnology.tk.eSkarga.web;

import java.io.Serializable;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;

/** Kod błędu dla administratora na stronie 500 – pozwala odnaleźć wpis w logach bez ujawniania szczegółów technicznych. */
@Named("errorCode")
@RequestScoped
public class ErrorCodeBean implements Serializable {

    @Inject
    private AppClock clock;

    private final String suffix = UUID.randomUUID().toString().substring(0, 4).toUpperCase();

    public String getCode() {
        return "RTS-500-" + DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmm").format(clock.now()) + "-" + suffix;
    }
}
