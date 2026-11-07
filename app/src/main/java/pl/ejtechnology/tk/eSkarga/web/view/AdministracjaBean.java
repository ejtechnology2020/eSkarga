package pl.ejtechnology.tk.eSkarga.web.view;

import jakarta.faces.context.FacesContext;
import jakarta.faces.application.FacesMessage;
import pl.ejtechnology.tk.eSkarga.documents.DocsGateway;
import pl.ejtechnology.tk.eSkarga.audit.AuditService;
import pl.ejtechnology.tk.eSkarga.domain.service.TemplateService;
import pl.ejtechnology.tk.eSkarga.domain.service.HolidayService;
import pl.ejtechnology.tk.eSkarga.domain.service.DelegationService;
import pl.ejtechnology.tk.eSkarga.security.CaseAccessPolicy;
import pl.ejtechnology.tk.eSkarga.security.CurrentUser;
import pl.ejtechnology.tk.eSkarga.security.OidcSettings;
import pl.ejtechnology.tk.eSkarga.web.ViewData;
import java.io.Serializable;
import java.util.List;

import jakarta.faces.view.ViewScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;

import pl.ejtechnology.tk.eSkarga.web.demo.DemoDataset;
import pl.ejtechnology.tk.eSkarga.web.model.AuditRow;
import pl.ejtechnology.tk.eSkarga.web.model.CalendarMonth;
import pl.ejtechnology.tk.eSkarga.web.model.DelegationRow;
import pl.ejtechnology.tk.eSkarga.web.model.DictionaryRow;
import pl.ejtechnology.tk.eSkarga.web.model.TemplateRow;
import pl.ejtechnology.tk.eSkarga.web.model.TimelineEntry;
import pl.ejtechnology.tk.eSkarga.web.model.UserRow;

/** Ekrany administracyjne: użytkownicy (S-22), upoważnienia (S-23), słowniki/szablony/dni wolne (S-24), dziennik zdarzeń (S-25). */
@Named("administracja")
@ViewScoped
public class AdministracjaBean implements Serializable {

    @Inject
    private ViewData data;

    @Inject
    private OidcSettings settings;
    @Inject
    private CurrentUser currentUser;
    @Inject
    private DelegationService delegations;
    @Inject
    private HolidayService holidays;
    @Inject
    private AuditService audit;
    @Inject
    private TemplateService templates;
    @Inject
    private DocsGateway docs;
    @Inject
    private pl.ejtechnology.tk.eSkarga.documents.DocumentStore store;
    /** Akt podstawowy przesłany w formularzu (do czasu zapisu trzymany w widoku; do S3 trafia razem z zapisem upoważnienia). */
    private transient byte[] aktTresc;
    private String aktNazwa;
    private java.time.LocalDate od;
    private java.time.LocalDate doDnia;
    private String akt;
    private java.time.LocalDate dzienWolny;
    private String opisDnia;
    private String kodSzablonu = "ZARZ_BRAKI";
    private String nazwaSzablonu;
    private String zrodloSzablonu;
    private List<String> bledySzablonu = List.of();
    /** Wersja szablonu w edytorze (null = nowy projekt) i wersja w panelu podglądu (PDF z usługi dokumentów). */
    private Long edytowanyId;
    private String edytowanyStan;
    private Long podgladId;
    private String podgladTytul;
    /** Lista kontrolna (S-24): repertorium, wymóg w edytorze (null = nowy) i jego pola. */
    private String symbolListy = "TS";
    private Long wymogId;
    private String wymogKod;
    private String wymogTresc;
    private String wymogPodstawa;
    private String wymogWarunek = "ZAWSZE";
    private int wymogKolejnosc = 999;
    /** Słownik sędziów (S-24, ADR 0027): wpis w edytorze (null = nowy) i jego pola. */
    private Long sedziaId;
    private String sedziaLogin;
    private String sedziaNazwa;
    private String sedziaFunkcja = "SEDZIA";
    private java.time.LocalDate sedziaOd;
    private java.time.LocalDate sedziaDo;
    private int tab = 2;
    /** Akt podstawowy (PDF) z p:fileUpload – zapamiętany w widoku do czasu „Zapisz upoważnienie”. */
    public void uploadAkt(org.primefaces.event.FileUploadEvent event) {
        org.primefaces.model.file.UploadedFile f = event.getFile();
        byte[] tresc = f.getContent();
        FacesContext fc = FacesContext.getCurrentInstance();
        if (!"application/pdf".equals(pl.ejtechnology.tk.eSkarga.domain.service.IncomingRegister.detectScanType(tresc))) {
            fc.addMessage("growl", new FacesMessage(FacesMessage.SEVERITY_ERROR, "Odrzucono " + f.getFileName(), "Akt podstawowy musi być plikiem PDF."));
            return;
        }
        if (tresc.length > 20L * 1024 * 1024) {
            fc.addMessage("growl", new FacesMessage(FacesMessage.SEVERITY_ERROR, "Odrzucono " + f.getFileName(), "Plik przekracza 20 MB."));
            return;
        }
        aktTresc = tresc;
        aktNazwa = f.getFileName() == null || f.getFileName().isBlank() ? "akt.pdf" : f.getFileName().replaceAll("[\\\\/\\p{Cntrl}]", "_");
        akt = aktNazwa;
        fc.addMessage("growl", new FacesMessage(FacesMessage.SEVERITY_INFO, "Dołączono " + aktNazwa, "Plik zapisze się razem z upoważnieniem."));
    }

    public void usunAkt() {
        aktTresc = null;
        aktNazwa = null;
        akt = null;
    }

    public String getAktNazwa() { return aktNazwa; }
    public boolean isAktDolaczony() { return aktTresc != null; }
    public String getAktRozmiar() { return aktTresc == null ? "" : String.format("%.1f MB", aktTresc.length / 1048576.0); }

    /** FR-36: nowe upoważnienie z aktem podstawowym – akt do repozytorium (S3, SHA-256), wpis z kluczem dokumentu, audyt. */
    public void addDelegation() {
        FacesContext fc = FacesContext.getCurrentInstance();
        if (settings.isDemo()) {
            fc.addMessage("growl", new FacesMessage(FacesMessage.SEVERITY_INFO, "Tryb demo: upoważnienie nie jest zapisywane.", null));
            return;
        }
        if (osoba == null || osoba.isBlank() || od == null || doDnia == null || aktTresc == null) {
            fc.addMessage(null, new FacesMessage(FacesMessage.SEVERITY_ERROR, "Uzupełnij osobę, okres od – do i dołącz akt podstawowy (PDF).", null));
            return;
        }
        try {
            String key = "upowaznienia/" + od.getYear() + "/" + java.util.UUID.randomUUID() + ".pdf";
            pl.ejtechnology.tk.eSkarga.documents.DocumentStore.Stored stored = store.put(key, new java.io.ByteArrayInputStream(aktTresc), aktTresc.length, "application/pdf", false);
            delegations.add(osoba, "prezes".equals(zakres) ? CaseAccessPolicy.FUNCTION_PRESIDENT : zakres, od, doDnia, aktNazwa, stored.key(), stored.sha256(), currentUser.getLogin());
            fc.addMessage("growl", new FacesMessage(FacesMessage.SEVERITY_INFO, "Upoważnienie dodane.", osoba));
            osoba = null;
            od = null;
            doDnia = null;
            usunAkt();
        } catch (RuntimeException e) {
            fc.addMessage(null, new FacesMessage(FacesMessage.SEVERITY_ERROR, "Nie udało się dodać upoważnienia.", e.getMessage()));
        }
    }
    public void revokeDelegation(String id) {
        if (!settings.isDemo()) {
            delegations.revoke(Long.parseLong(id), currentUser.getLogin());
        }
    }
    /** NFR-02: weryfikacja łańcucha skrótów dziennika zdarzeń (S-25). */
    public void verifyChain() {
        if (settings.isDemo()) {
            return;
        }
        var r = audit.verifyChain();
        lancuch = r.consistent() ? "spojny" : "alarm";
        FacesContext.getCurrentInstance().addMessage("growl", new FacesMessage(r.consistent() ? FacesMessage.SEVERITY_INFO : FacesMessage.SEVERITY_ERROR,
                r.consistent() ? "Łańcuch spójny." : "Naruszenie łańcucha!", r.consistent() ? "Sprawdzono " + r.checked() + " wpisów."
                        : "Pierwszy niezgodny wpis: " + r.firstBroken().map(e -> String.valueOf(e.getId())).orElse("?")));
    }
    public void addHoliday() {
        if (!settings.isDemo() && dzienWolny != null) {
            holidays.add(dzienWolny, opisDnia == null ? "dzień wolny w Trybunale" : opisDnia, currentUser.getLogin());
        }
    }
    /** S-24 (T-026a): walidacja szablonu w usłudze dokumentów (kompilacja, render na Ts 312/26, veraPDF) przed zapisem i aktywacją. */
    public void validateTemplate() {
        if (settings.isDemo()) {
            bledySzablonu = zrodloSzablonu != null && zrodloSzablonu.contains("${") && !zrodloSzablonu.contains("}") ? List.of("niezamknięte wyrażenie ${") : List.of();
            return;
        }
        var r = docs.validateTemplate(kodSzablonu, zrodloSzablonu);
        bledySzablonu = r.errors();
        FacesContext.getCurrentInstance().addMessage("growl", new FacesMessage(r.ok() ? FacesMessage.SEVERITY_INFO : FacesMessage.SEVERITY_WARN,
                r.ok() ? "Szablon poprawny." : "Szablon z błędami: " + r.errors().size(), null));
    }
    public void saveTemplateDraft() {
        if (settings.isDemo()) {
            return;
        }
        try {
            var t = templates.saveDraft(kodSzablonu, nazwaSzablonu == null ? kodSzablonu : nazwaSzablonu, zrodloSzablonu, currentUser.getLogin());
            FacesContext.getCurrentInstance().addMessage("growl", new FacesMessage(FacesMessage.SEVERITY_INFO, "Zapisano projekt szablonu.", t.getCode() + " v" + t.getVersion()));
        } catch (RuntimeException e) {
            FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(FacesMessage.SEVERITY_ERROR, "Nie udało się zapisać szablonu.", e.getMessage()));
        }
    }
    public void activateTemplate(long templateId) {
        if (settings.isDemo()) {
            return;
        }
        try {
            var v = templates.activate(templateId, currentUser.getLogin());
            bledySzablonu = v.errors();
            FacesContext.getCurrentInstance().addMessage("growl", new FacesMessage(v.ok() ? FacesMessage.SEVERITY_INFO : FacesMessage.SEVERITY_ERROR,
                    v.ok() ? "Szablon aktywowany." : "Aktywacja odrzucona przez walidację.", null));
        } catch (RuntimeException e) {
            FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(FacesMessage.SEVERITY_ERROR, "Nie udało się aktywować szablonu.", e.getMessage()));
        }
    }
    /** S-24: wczytanie wersji do edytora – projekt poprawiany w miejscu, aktywna/wycofana → zapis tworzy nową wersję (D13). */
    public void editTemplate(TemplateRow t) {
        if (settings.isDemo()) {
            kodSzablonu = t.kod();
            nazwaSzablonu = t.nazwa();
            zrodloSzablonu = "<p>Tryb demo: źródło szablonu jest w bazie.</p>";
            edytowanyId = t.id();
            edytowanyStan = t.stan();
            return;
        }
        templates.find(t.id()).ifPresent(v -> {
            kodSzablonu = v.getCode();
            nazwaSzablonu = v.getName();
            zrodloSzablonu = v.getSourceXhtml();
            edytowanyId = v.getId();
            edytowanyStan = v.getStatus().name().toLowerCase();
        });
        bledySzablonu = List.of();
    }

    public void newTemplate() {
        edytowanyId = null;
        edytowanyStan = null;
        nazwaSzablonu = null;
        zrodloSzablonu = null;
        bledySzablonu = List.of();
    }

    /** Zapis z edytora: projekt w miejscu ({@code updateDraft}), inaczej nowa wersja PROJEKT ({@code saveDraft}). */
    public void saveTemplate() {
        if (settings.isDemo()) {
            FacesContext.getCurrentInstance().addMessage("growl", new FacesMessage(FacesMessage.SEVERITY_INFO, "Tryb demo: szablon nie jest zapisywany.", null));
            return;
        }
        if (kodSzablonu == null || kodSzablonu.isBlank() || zrodloSzablonu == null || zrodloSzablonu.isBlank()) {
            FacesContext.getCurrentInstance().addMessage("growl", new FacesMessage(FacesMessage.SEVERITY_WARN, "Podaj kod i źródło szablonu.", null));
            return;
        }
        try {
            String nazwa = nazwaSzablonu == null || nazwaSzablonu.isBlank() ? kodSzablonu : nazwaSzablonu;
            var t = edytowanyId != null && "projekt".equals(edytowanyStan)
                    ? templates.updateDraft(edytowanyId, nazwa, zrodloSzablonu, currentUser.getLogin())
                    : templates.saveDraft(kodSzablonu.trim().toUpperCase(), nazwa, zrodloSzablonu, currentUser.getLogin());
            edytowanyId = t.getId();
            edytowanyStan = "projekt";
            podgladId = t.getId();
            podgladTytul = t.getName() + " v" + t.getVersion();
            FacesContext.getCurrentInstance().addMessage("growl", new FacesMessage(FacesMessage.SEVERITY_INFO, "Zapisano projekt szablonu.", t.getCode() + " v" + t.getVersion()));
        } catch (RuntimeException e) {
            FacesContext.getCurrentInstance().addMessage("growl", new FacesMessage(FacesMessage.SEVERITY_ERROR, "Nie udało się zapisać szablonu.", e.getMessage()));
        }
    }

    /** Podgląd wersji (PDF z usługi dokumentów na danych fikcyjnych Ts 312/26) w panelu obok listy. */
    public void previewTemplate(TemplateRow t) {
        podgladId = t.id();
        podgladTytul = t.nazwa() + " " + t.wersja();
    }

    public void withdrawTemplate(long templateId) {
        if (settings.isDemo()) {
            return;
        }
        try {
            var t = templates.withdraw(templateId, currentUser.getLogin());
            FacesContext.getCurrentInstance().addMessage("growl", new FacesMessage(FacesMessage.SEVERITY_WARN, "Wycofano " + t.getCode() + " v" + t.getVersion() + ".",
                    "Do aktywacji innej wersji pisma z tego szablonu nie powstaną."));
        } catch (RuntimeException e) {
            FacesContext.getCurrentInstance().addMessage("growl", new FacesMessage(FacesMessage.SEVERITY_ERROR, "Nie udało się wycofać szablonu.", e.getMessage()));
        }
    }

    /** Usunięcie logiczne wersji (projekt albo wycofana); aktywnej nie – usługa odmawia. */
    public void deleteTemplate(long templateId) {
        if (settings.isDemo()) {
            return;
        }
        try {
            var t = templates.delete(templateId, currentUser.getLogin());
            if (podgladId != null && podgladId == templateId) {
                podgladId = null;
            }
            if (edytowanyId != null && edytowanyId == templateId) {
                newTemplate();
            }
            FacesContext.getCurrentInstance().addMessage("growl", new FacesMessage(FacesMessage.SEVERITY_INFO, "Usunięto " + t.getCode() + " v" + t.getVersion() + ".", null));
        } catch (RuntimeException e) {
            FacesContext.getCurrentInstance().addMessage("growl", new FacesMessage(FacesMessage.SEVERITY_ERROR, "Nie udało się usunąć szablonu.", e.getMessage()));
        }
    }

    /** Kody szablonów do wyboru: rodzaje pism aplikacji + kody już obecne w rejestrze. */
    public List<String> getKodySzablonow() {
        java.util.Set<String> out = new java.util.TreeSet<>();
        for (var k : pl.ejtechnology.tk.eSkarga.domain.service.LetterKind.values()) {
            out.add(k.name());
        }
        for (TemplateRow t : getSzablony()) {
            if (t.kod() != null) {
                out.add(t.kod());
            }
        }
        if (kodSzablonu != null && !kodSzablonu.isBlank()) {
            out.add(kodSzablonu);
        }
        return new java.util.ArrayList<>(out);
    }

    public Long getEdytowanyId() { return edytowanyId; }
    public String getEdytowanyStan() { return edytowanyStan; }
    public Long getPodgladId() { return podgladId; }
    public String getPodgladTytul() { return podgladTytul; }
    /** Adres PDF podglądu (TemplatePreviewServlet) albo null, gdy nic nie wybrano / tryb demo. */
    public String getPodgladUrl() {
        return podgladId == null || settings.isDemo() ? null : "/admin/szablony/podglad?id=" + podgladId + "&v=" + System.nanoTime();
    }

    // ---- słownik sędziów (S-24, ADR 0027)
    @Inject
    private pl.ejtechnology.tk.eSkarga.domain.service.JudgeDictionaryService sedziowie;

    public List<pl.ejtechnology.tk.eSkarga.web.model.JudgeRow> getSedziowieSlownik() { return data.judgesDictionary(); }
    public List<pl.ejtechnology.tk.eSkarga.web.model.AccountRow> getKontaSedziow() { return data.judgeAccounts(); }
    public List<pl.ejtechnology.tk.eSkarga.domain.JudgeFunction> getFunkcje() { return java.util.Arrays.asList(pl.ejtechnology.tk.eSkarga.domain.JudgeFunction.values()); }

    public void editJudge(pl.ejtechnology.tk.eSkarga.web.model.JudgeRow r) {
        sedziaId = r.id();
        sedziaLogin = r.login();
        sedziaNazwa = r.nazwa();
        sedziaFunkcja = r.funkcja();
        sedziaOd = r.od();
        sedziaDo = r.doDnia();
    }

    public void newJudge() {
        sedziaId = null;
        sedziaLogin = null;
        sedziaNazwa = null;
        sedziaFunkcja = "SEDZIA";
        sedziaOd = null;
        sedziaDo = null;
    }

    /** Nowy wpis albo zmiana (login stały – sprawy i zarządzenia odwołują się do loginu). */
    public void saveJudge() {
        FacesContext fc = FacesContext.getCurrentInstance();
        if (settings.isDemo()) {
            fc.addMessage("growl", new FacesMessage(FacesMessage.SEVERITY_INFO, "Tryb demo: wpis nie jest zapisywany.", null));
            return;
        }
        try {
            var funkcja = pl.ejtechnology.tk.eSkarga.domain.JudgeFunction.valueOf(sedziaFunkcja);
            var j = sedziaId == null ? sedziowie.add(sedziaLogin, sedziaNazwa, funkcja, sedziaOd, sedziaDo, currentUser.getLogin())
                    : sedziowie.update(sedziaId, sedziaNazwa, funkcja, sedziaOd, sedziaDo, currentUser.getLogin());
            fc.addMessage("growl", new FacesMessage(FacesMessage.SEVERITY_INFO, (sedziaId == null ? "Dodano do słownika: " : "Zapisano: ") + j.titledName() + ".",
                    "Kadencja " + j.getTermFrom() + " – " + (j.getTermTo() == null ? "…" : j.getTermTo())));
            newJudge();
        } catch (RuntimeException e) {
            fc.addMessage("growl", new FacesMessage(FacesMessage.SEVERITY_ERROR, "Nie udało się zapisać sędziego.", e.getMessage()));
        }
    }

    public void toggleJudge(long id, boolean active) {
        if (settings.isDemo()) {
            return;
        }
        try {
            var j = sedziowie.setActive(id, active, currentUser.getLogin());
            FacesContext.getCurrentInstance().addMessage("growl", new FacesMessage(FacesMessage.SEVERITY_INFO, (active ? "Przywrócono " : "Wycofano ") + j.titledName() + ".", null));
            if (sedziaId != null && sedziaId == id && !active) {
                newJudge();
            }
        } catch (RuntimeException e) {
            FacesContext.getCurrentInstance().addMessage("growl", new FacesMessage(FacesMessage.SEVERITY_ERROR, "Nie udało się zmienić wpisu.", e.getMessage()));
        }
    }

    public Long getSedziaId() { return sedziaId; }
    public String getSedziaLogin() { return sedziaLogin; }
    public void setSedziaLogin(String v) { sedziaLogin = v; }
    public String getSedziaNazwa() { return sedziaNazwa; }
    public void setSedziaNazwa(String v) { sedziaNazwa = v; }
    public String getSedziaFunkcja() { return sedziaFunkcja; }
    public void setSedziaFunkcja(String v) { sedziaFunkcja = v; }
    public java.time.LocalDate getSedziaOd() { return sedziaOd; }
    public void setSedziaOd(java.time.LocalDate v) { sedziaOd = v; }
    public java.time.LocalDate getSedziaDo() { return sedziaDo; }
    public void setSedziaDo(java.time.LocalDate v) { sedziaDo = v; }

    // ---- lista kontrolna wymogów formalnych (S-24, UC-06, ADR 0015)
    @Inject
    private pl.ejtechnology.tk.eSkarga.domain.service.RequirementCatalogService wymogi;

    public List<pl.ejtechnology.tk.eSkarga.web.model.RequirementRow> getWymogi() {
        return data.requirements(pl.ejtechnology.tk.eSkarga.domain.DocketSymbol.valueOf(symbolListy));
    }

    public List<pl.ejtechnology.tk.eSkarga.domain.RequirementCondition> getWarunki() {
        return java.util.Arrays.asList(pl.ejtechnology.tk.eSkarga.domain.RequirementCondition.values());
    }

    public void editRequirement(pl.ejtechnology.tk.eSkarga.web.model.RequirementRow r) {
        wymogId = r.id();
        wymogKod = r.kod();
        wymogTresc = r.wymog();
        wymogPodstawa = r.podstawa();
        wymogWarunek = r.warunek();
        wymogKolejnosc = r.kolejnosc();
    }

    public void newRequirement() {
        wymogId = null;
        wymogKod = null;
        wymogTresc = null;
        wymogPodstawa = null;
        wymogWarunek = "ZAWSZE";
        wymogKolejnosc = getWymogi().stream().mapToInt(pl.ejtechnology.tk.eSkarga.web.model.RequirementRow::kolejnosc).max().orElse(0) + 10;
    }

    /** Nowy wymóg albo zmiana istniejącego (kod stały – oceny w aktach odwołują się do kodu). */
    public void saveRequirement() {
        FacesContext fc = FacesContext.getCurrentInstance();
        if (settings.isDemo()) {
            fc.addMessage("growl", new FacesMessage(FacesMessage.SEVERITY_INFO, "Tryb demo: wymóg nie jest zapisywany.", null));
            return;
        }
        try {
            var warunek = pl.ejtechnology.tk.eSkarga.domain.RequirementCondition.valueOf(wymogWarunek);
            var symbol = pl.ejtechnology.tk.eSkarga.domain.DocketSymbol.valueOf(symbolListy);
            var r = wymogId == null ? wymogi.add(symbol, wymogKod, wymogTresc, wymogPodstawa, warunek, wymogKolejnosc, currentUser.getLogin())
                    : wymogi.update(wymogId, wymogTresc, wymogPodstawa, warunek, wymogKolejnosc, currentUser.getLogin());
            fc.addMessage("growl", new FacesMessage(FacesMessage.SEVERITY_INFO, (wymogId == null ? "Dodano wymóg " : "Zapisano wymóg ") + r.getCode() + " (" + symbol.code() + ").",
                    "Obowiązuje dla ocen od teraz; oceny zapisane w aktach pozostają bez zmian."));
            newRequirement();
        } catch (RuntimeException e) {
            fc.addMessage("growl", new FacesMessage(FacesMessage.SEVERITY_ERROR, "Nie udało się zapisać wymogu.", e.getMessage()));
        }
    }

    public void toggleRequirement(long id, boolean active) {
        if (settings.isDemo()) {
            return;
        }
        try {
            var r = wymogi.setActive(id, active, currentUser.getLogin());
            FacesContext.getCurrentInstance().addMessage("growl", new FacesMessage(FacesMessage.SEVERITY_INFO, (active ? "Przywrócono " : "Wycofano ") + r.getCode() + ".", null));
            if (wymogId != null && wymogId == id && !active) {
                newRequirement();
            }
        } catch (RuntimeException e) {
            FacesContext.getCurrentInstance().addMessage("growl", new FacesMessage(FacesMessage.SEVERITY_ERROR, "Nie udało się zmienić wymogu.", e.getMessage()));
        }
    }

    public String getSymbolListy() { return symbolListy; }
    public void setSymbolListy(String s) { if (s != null && !s.equals(symbolListy)) { symbolListy = s; newRequirement(); } }
    public Long getWymogId() { return wymogId; }
    public String getWymogKod() { return wymogKod; }
    public void setWymogKod(String v) { wymogKod = v; }
    public String getWymogTresc() { return wymogTresc; }
    public void setWymogTresc(String v) { wymogTresc = v; }
    public String getWymogPodstawa() { return wymogPodstawa; }
    public void setWymogPodstawa(String v) { wymogPodstawa = v; }
    public String getWymogWarunek() { return wymogWarunek; }
    public void setWymogWarunek(String v) { wymogWarunek = v; }
    public int getWymogKolejnosc() { return wymogKolejnosc; }
    public void setWymogKolejnosc(int v) { wymogKolejnosc = v; }

    public void seedTemplates() {
        if (!settings.isDemo()) {
            int n = templates.seedDefaults(docs.defaultTemplates(), currentUser.getLogin());
            FacesContext.getCurrentInstance().addMessage("growl", new FacesMessage(FacesMessage.SEVERITY_INFO, "Szablony domyślne: " + n, null));
        }
    }
    public java.time.LocalDate getOd() { return od; }
    public void setOd(java.time.LocalDate od) { this.od = od; }
    public java.time.LocalDate getDoDnia() { return doDnia; }
    public void setDoDnia(java.time.LocalDate d) { this.doDnia = d; }
    public String getAkt() { return akt; }
    public void setAkt(String akt) { this.akt = akt; }
    public java.time.LocalDate getDzienWolny() { return dzienWolny; }
    public void setDzienWolny(java.time.LocalDate d) { this.dzienWolny = d; }
    public String getOpisDnia() { return opisDnia; }
    public void setOpisDnia(String o) { this.opisDnia = o; }
    public String getKodSzablonu() { return kodSzablonu; }
    public void setKodSzablonu(String k) { this.kodSzablonu = k; }
    public String getNazwaSzablonu() { return nazwaSzablonu; }
    public void setNazwaSzablonu(String n) { this.nazwaSzablonu = n; }
    public String getZrodloSzablonu() { return zrodloSzablonu; }
    public void setZrodloSzablonu(String z) { this.zrodloSzablonu = z; }
    public List<String> getBledySzablonu() { return bledySzablonu; }
    private String zakladka;
    private String lancuch = "spojny";
    private int rok = 2026;
    private String osoba;
    private String zakres = "prezes";
    private String uzytkownikFiltr;
    private String sprawaFiltr;

    public void applyState() {
        if (zakladka != null) {
            tab = switch (zakladka) { case "slowniki" -> 0; case "szablony" -> 1; case "lista" -> 3; case "sedziowie" -> 4; default -> 2; };
        }
    }

    public List<UserRow> getUzytkownicy() { return data.users(); }
    public List<DelegationRow> getUpowaznienia() { return data.delegations(); }
    public List<TemplateRow> getSzablony() { return data.templates(); }
    public java.util.Map<String, Integer> getLiczniki() { return data.dictionaryCounts(); }
    public List<DictionaryRow> getWzorce() { return data.reviewStandards(); }
    public List<CalendarMonth> getKalendarz() { return data.calendar(rok); }
    public List<AuditRow> getZdarzenia() { return data.audit(); }
    public AuditRow getWybraneZdarzenie() { var a = data.audit(); return a.isEmpty() ? null : a.get(0); }
    public boolean isAlarm() { return "alarm".equals(lancuch); }

    public List<TimelineEntry> getHistoriaUpowaznienia() {
        return List.of(new TimelineEntry(DemoDataset.dt(2026, 10, 30, 12, 10), "Utworzono", "· 30.10–31.12.2026 · Anna Admin", null, true, true),
                new TimelineEntry(DemoDataset.dt(2026, 10, 30, 12, 14), "Dołączono akt", "zarządzenie nr 12/2026 · Anna Admin", null, false, true));
    }

    public int getTab() { return tab; }
    public void setTab(int tab) { this.tab = tab; }
    public String getZakladka() { return zakladka; }
    public void setZakladka(String zakladka) { this.zakladka = zakladka; }
    public String getLancuch() { return lancuch; }
    public void setLancuch(String lancuch) { this.lancuch = lancuch; }
    public int getRok() { return rok; }
    public void setRok(int rok) { this.rok = rok; }
    public void prevYear() { rok--; }
    public void nextYear() { rok++; }
    public String getOsoba() { return osoba; }
    public void setOsoba(String osoba) { this.osoba = osoba; }
    public String getZakres() { return zakres; }
    public void setZakres(String zakres) { this.zakres = zakres; }
    public String getUzytkownikFiltr() { return uzytkownikFiltr; }
    public void setUzytkownikFiltr(String u) { this.uzytkownikFiltr = u; }
    public String getSprawaFiltr() { return sprawaFiltr; }
    public void setSprawaFiltr(String s) { this.sprawaFiltr = s; }
}
