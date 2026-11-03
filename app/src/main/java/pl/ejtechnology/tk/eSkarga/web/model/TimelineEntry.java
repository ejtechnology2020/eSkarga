package pl.ejtechnology.tk.eSkarga.web.model;

import java.io.Serializable;
import java.time.LocalDateTime;

/** Element osi czasu (czynności, powiadomienia, historia wersji). */
public record TimelineEntry(LocalDateTime when, String who, String what, String href, boolean current, boolean showTime)
        implements Serializable {

    public static TimelineEntry of(LocalDateTime when, String who, String what) {
        return new TimelineEntry(when, who, what, null, false, true);
    }
}
