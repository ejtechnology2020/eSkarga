package pl.ejtechnology.tk.eSkarga.web.view;

import jakarta.servlet.http.HttpServletRequest;
import pl.ejtechnology.tk.eSkarga.domain.service.IncomingRegister;
import pl.ejtechnology.tk.eSkarga.security.CurrentUser;
import pl.ejtechnology.tk.eSkarga.security.OidcSettings;
import pl.ejtechnology.tk.eSkarga.web.ViewData;
import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import jakarta.annotation.PostConstruct;
import jakarta.faces.application.FacesMessage;
import jakarta.faces.context.FacesContext;
import jakarta.faces.view.ViewScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;

import pl.ejtechnology.tk.eSkarga.domain.IntakeChannel;
import pl.ejtechnology.tk.eSkarga.web.AppClock;
import pl.ejtechnology.tk.eSkarga.web.Fmt;
import pl.ejtechnology.tk.eSkarga.web.demo.DemoDataset;
import pl.ejtechnology.tk.eSkarga.web.model.TimelineEntry;

/**
 * S-03 Rejestracja wpływu pisma (UC-01, FR-01 – FR-03). Jeden formularz, rejestracja w mniej niż 3 minuty (NFR-14).
 * Stany: pusty, wypełniony, pismo elektroniczne (pola z UPO), błąd walidacji, potwierdzenie z numerem dziennika.
 * Wariant dokumentu z klauzulą zapisuje wyłącznie metadane (UC-01 3a, NFR-06).
 */
@Named("rejestracja")
@ViewScoped
public class RejestracjaBean implements Serializable {

    @Inject
    private ViewData data;
    @Inject
    private AppClock clock;
    @Inject
    private Fmt fmt;
    @Inject
    private OidcSettings settings;
    @Inject
    private CurrentUser currentUser;
    @Inject
    private IncomingRegister incoming;
    @Inject
    private HttpServletRequest request;

    private String stan = "wypelniony";
    private IntakeChannel kanal = IntakeChannel.POCZTA;
    private LocalDateTime wplyw;
    private String wniesienie;
    private boolean dataNiepewna;
    private String nadawca;
    private Integer zalaczniki;
    private Integer odpisy;
    private boolean klauzula;
    private boolean zarejestrowano;
    private String numerDziennika;
    private boolean hasFiles;
    /** Pliki wysłane w formularzu (tryb z bazą), zapisywane do repozytorium przy rejestracji. */
    private final List<Plik> pliki = new java.util.ArrayList<>();

    public record Plik(String nazwa, long rozmiar, byte[] tresc) implements Serializable {
        public String rozmiarTekst() {
            return rozmiar >= 1_048_576 ? String.format("%.1f MB", rozmiar / 1_048_576.0) : String.format("%d kB", Math.max(1, rozmiar / 1024));
        }
    }

    /** p:fileUpload (listener): plik trafia do listy; format i rozmiar sprawdza ostatecznie IncomingRegister przy zapisie (T-055). */
    public void upload(org.primefaces.event.FileUploadEvent event) {
        org.primefaces.model.file.UploadedFile f = event.getFile();
        FacesContext fc = FacesContext.getCurrentInstance();
        byte[] tresc = f.getContent();
        if (IncomingRegister.detectScanType(tresc) == null) {
            fc.addMessage("growl", new FacesMessage(FacesMessage.SEVERITY_ERROR, "Odrzucono " + f.getFileName(), "Dozwolone są tylko pliki PDF lub TIFF."));
            return;
        }
        if (tresc.length > IncomingRegister.MAX_SCAN_BYTES) {
            fc.addMessage("growl", new FacesMessage(FacesMessage.SEVERITY_ERROR, "Odrzucono " + f.getFileName(), "Plik przekracza 50 MB."));
            return;
        }
        pliki.add(new Plik(f.getFileName(), tresc.length, tresc));
        if (zalaczniki == null || zalaczniki < pliki.size()) {
            zalaczniki = pliki.size();
        }
        fc.addMessage("growl", new FacesMessage(FacesMessage.SEVERITY_INFO, "Dodano " + f.getFileName(), null));
    }

    public void usunPlik(int indeks) {
        if (indeks >= 0 && indeks < pliki.size()) {
            pliki.remove(indeks);
        }
    }

    public List<Plik> getPliki() { return pliki; }

    @PostConstruct
    void init() {
        wplyw = LocalDateTime.of(DemoDataset.TODAY, java.time.LocalTime.of(8, 42));
    }

    /** Parametr {@code stan} pozwala obejrzeć warianty formularza jak w makiecie (pusty, el, blad, ok). */
    public void applyState() {
        switch (stan == null ? "" : stan) {
            case "pusty" -> {
                kanal = null; wniesienie = null; nadawca = null; zalaczniki = null; odpisy = null; hasFiles = false;
            }
            case "el" -> {
                kanal = IntakeChannel.EDORECZENIA; wniesienie = "02.11.2026 23:14";
                nadawca = "Rada Gminy Przykładowo (adres do e-Doręczeń AE:PL-12345-67890-ABCDE-12)"; zalaczniki = 3; odpisy = 0; hasFiles = true;
            }
            case "blad" -> {
                kanal = null; wniesienie = "31.13.2026"; nadawca = null; zalaczniki = null; odpisy = null; hasFiles = false;
                FacesContext fc = FacesContext.getCurrentInstance();
                fc.addMessage(null, new FacesMessage(FacesMessage.SEVERITY_ERROR, fmt.msg("msg.fixFields", 3), null));
                fc.addMessage("f-kanal", new FacesMessage(FacesMessage.SEVERITY_ERROR, fmt.msg("msg.channel.required"), null));
                fc.addMessage("f-wn", new FacesMessage(FacesMessage.SEVERITY_ERROR, "Miesiąc 13 nie istnieje. Podaj datę w formacie DD.MM.RRRR.", null));
                fc.addMessage("f-nad", new FacesMessage(FacesMessage.SEVERITY_ERROR, fmt.msg("msg.sender.required"), null));
            }
            case "ok" -> {
                fill(); register();
            }
            default -> {
                if (settings.isDemo()) {
                    fill();
                } else {
                    kanal = null; wniesienie = null; nadawca = null; zalaczniki = null; odpisy = null; hasFiles = false;   // z bazą: pusty formularz
                }
            }
        }
    }

    private void fill() {
        kanal = IntakeChannel.POCZTA; wniesienie = "31.10.2026"; nadawca = "adw. Anna Testowa, Kancelaria Adwokacka w Przykładowie";
        zalaczniki = 1; odpisy = 0; hasFiles = true;
    }

    /** Akcja główna: „Zarejestruj i drukuj etykietę”. */
    public void register() {
        FacesContext fc = FacesContext.getCurrentInstance();
        boolean ok = true;
        if (kanal == null) {
            fc.addMessage("f-kanal", new FacesMessage(FacesMessage.SEVERITY_ERROR, fmt.msg("msg.channel.required"), null));
            ok = false;
        }
        if (wniesienie == null || !parseDate(wniesienie)) {
            fc.addMessage("f-wn", new FacesMessage(FacesMessage.SEVERITY_ERROR, fmt.msg("msg.date.invalid", "Data wniesienia"), null));
            ok = false;
        }
        if (nadawca == null || nadawca.isBlank()) {
            fc.addMessage("f-nad", new FacesMessage(FacesMessage.SEVERITY_ERROR, fmt.msg("msg.sender.required"), null));
            ok = false;
        }
        if (!ok) {
            fc.addMessage(null, new FacesMessage(FacesMessage.SEVERITY_ERROR, fmt.msg("msg.fixFields", 3), null));
            return;
        }
        if (settings.isDemo()) {
            numerDziennika = "DW 2026/04812";
        } else {
            try {
                LocalDate filedOn = LocalDate.parse(wniesienie.trim().substring(0, 10), Fmt.DATE);
                var letter = incoming.register(new IncomingRegister.RegisterCommand(kanal, wplyw.atZone(AppClock.WARSAW).toInstant(), filedOn, dataNiepewna, nadawca,
                        zalaczniki == null ? 0 : zalaczniki, odpisy == null ? 0 : odpisy, klauzula, currentUser.getLogin(), request.getRemoteAddr()));
                numerDziennika = letter.getRegisterNo();
                if (!klauzula) {
                    for (Plik plik : pliki) {
                        incoming.attachScan(letter.getId(), plik.nazwa(), plik.tresc(), currentUser.getLogin(), request.getRemoteAddr());
                    }
                }
            } catch (RuntimeException e) {
                fc.addMessage(null, new FacesMessage(FacesMessage.SEVERITY_ERROR, "Nie udało się zarejestrować pisma.", e.getMessage()));
                return;
            }
        }
        zarejestrowano = true;
        fc.addMessage("growl", new FacesMessage(FacesMessage.SEVERITY_INFO, fmt.msg("msg.registered", numerDziennika),
                fmt.msg("msg.registered.detail", fmt.dateTime(wplyw), "BP-1")));
    }

    public void registerAndNext() {
        register();
        if (zarejestrowano) {
            zarejestrowano = false;
            stan = "pusty";
            applyState();
        }
    }

    public void clear() {
        stan = "pusty";
        applyState();
    }

    static boolean parseDate(String s) {
        try {
            LocalDate.parse(s.trim().substring(0, 10), Fmt.DATE);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public boolean isElectronic() {
        return kanal != null && kanal.isElectronic();
    }

    public List<TimelineEntry> getRecent() {
        return data.recentIntake();
    }

    public String getStan() { return stan; }
    public void setStan(String stan) { this.stan = stan; }
    public IntakeChannel getKanal() { return kanal; }
    public void setKanal(IntakeChannel kanal) { this.kanal = kanal; }
    public LocalDateTime getWplyw() { return wplyw; }
    public void setWplyw(LocalDateTime wplyw) { this.wplyw = wplyw; }
    public String getWniesienie() { return wniesienie; }
    public void setWniesienie(String wniesienie) { this.wniesienie = wniesienie; }
    public boolean isDataNiepewna() { return dataNiepewna; }
    public void setDataNiepewna(boolean dataNiepewna) { this.dataNiepewna = dataNiepewna; }
    public String getNadawca() { return nadawca; }
    public void setNadawca(String nadawca) { this.nadawca = nadawca; }
    public Integer getZalaczniki() { return zalaczniki; }
    public void setZalaczniki(Integer zalaczniki) { this.zalaczniki = zalaczniki; }
    public Integer getOdpisy() { return odpisy; }
    public void setOdpisy(Integer odpisy) { this.odpisy = odpisy; }
    public boolean isKlauzula() { return klauzula; }
    public void setKlauzula(boolean klauzula) { this.klauzula = klauzula; }
    public boolean isZarejestrowano() { return zarejestrowano; }
    public String getNumerDziennika() { return numerDziennika; }
    public boolean isHasFiles() { return settings.isDemo() ? hasFiles : !pliki.isEmpty(); }
    public IntakeChannel[] getKanaly() { return IntakeChannel.values(); }
}
