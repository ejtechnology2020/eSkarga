package pl.ejtechnology.tk.eSkarga.web;

import java.io.Serializable;
import java.text.MessageFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.ResourceBundle;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Named;

import pl.ejtechnology.tk.eSkarga.web.model.DeadlineView;

/**
 * Formatowanie dat i opisów terminów po polsku. Wzorce tekstów są w pakiecie komunikatów,
 * tu jest tylko logika (odmiana „dzień/dni”, „jutro”, „po terminie”).
 */
@Named("fmt")
@ApplicationScoped
public class Fmt implements Serializable {

    public static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    public static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");
    public static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");
    public static final DateTimeFormatter TIME_S = DateTimeFormatter.ofPattern("HH:mm:ss");
    public static final Locale PL = Locale.of("pl", "PL");

    private transient ResourceBundle bundle;

    private ResourceBundle bundle() {
        if (bundle == null) {
            bundle = ResourceBundle.getBundle("pl.ejtechnology.tk.eSkarga.web.messages", PL);
        }
        return bundle;
    }

    public String msg(String key, Object... args) {
        String pattern = bundle().containsKey(key) ? bundle().getString(key) : key;
        return args.length == 0 ? pattern : new MessageFormat(pattern, PL).format(args);
    }

    public String date(LocalDate d) {
        return d == null ? "—" : DATE.format(d);
    }

    public String dateTime(LocalDateTime d) {
        return d == null ? "—" : DATE_TIME.format(d);
    }

    public String time(LocalDateTime d) {
        return d == null ? "" : TIME.format(d);
    }

    public String timeSeconds(LocalDateTime d) {
        return d == null ? "" : TIME_S.format(d);
    }

    /** „1 dzień”, „6 dni”, „0 dni”. */
    public String days(long n) {
        long abs = Math.abs(n);
        return msg(abs == 1 ? "deadline.days.one" : "deadline.days.many", abs);
    }

    /** „za 6 dni”, „jutro”, „dziś”, „4 dni po terminie”. */
    public String daysText(long daysLeft) {
        if (daysLeft < 0) {
            return msg("deadline.overdue", days(daysLeft));
        }
        if (daysLeft == 0) {
            return msg("deadline.today");
        }
        if (daysLeft == 1) {
            return msg("deadline.tomorrow");
        }
        return msg("deadline.inDays", days(daysLeft));
    }

    /** „do 09.11.2026 · za 6 dni”. */
    public String untilText(LocalDate end, LocalDate today) {
        long left = ChronoUnit.DAYS.between(today, end);
        return msg("deadline.until", date(end)) + " · " + daysText(left);
    }

    public String waitingText(long days) {
        return msg("deadline.waiting", days(days));
    }

    public String deadlineText(DeadlineView d) {
        return d == null ? "" : d.text();
    }
}
