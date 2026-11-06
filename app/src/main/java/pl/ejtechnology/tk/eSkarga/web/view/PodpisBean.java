package pl.ejtechnology.tk.eSkarga.web.view;

import jakarta.faces.context.FacesContext;
import jakarta.faces.application.FacesMessage;
import jakarta.inject.Inject;
import pl.ejtechnology.tk.eSkarga.domain.service.LetterKind;
import pl.ejtechnology.tk.eSkarga.domain.service.SigningService;
import pl.ejtechnology.tk.eSkarga.security.CurrentUser;
import pl.ejtechnology.tk.eSkarga.security.OidcSettings;
import java.io.Serializable;
import java.util.List;
import java.util.Map;

import jakarta.faces.view.ViewScoped;
import jakarta.inject.Named;

import pl.ejtechnology.tk.eSkarga.web.model.Effect;

/**
 * S-16 Podpis kwalifikowany (D10, FR-16): okno z krokami – Podsumowanie, Aplikacja do podpisu, Karta i PIN, Wynik.
 * Serwer liczy dane do podpisu (DSS getDataToSign), aplikacja lokalna podpisuje skrót kartą, serwer składa podpis
 * i znacznik czasu. PIN nigdy nie jest wpisywany na stronie. Błędy: brak aplikacji, brak karty, błędny PIN,
 * certyfikat nieważny, przerwane połączenie, podpis odrzucony przy weryfikacji. Skład 3 sędziów: lista podpisujących.
 */
@Named("podpis")
@ViewScoped
public class PodpisBean implements Serializable {

    /** Błąd kroku podpisu z komunikatem dla użytkownika i kodem dla administratora. */
    public record SignError(String title, String text, String code, String action) implements Serializable {
    }

    private static final Map<String, SignError> ERRORS = Map.of(
            "brak-aplikacji", new SignError("Nie wykryto aplikacji do podpisu", "Uruchom aplikację do podpisu na tym komputerze albo zainstaluj ją z Portalu pracownika (Oprogramowanie › Podpis kwalifikowany).", "SIG-APP-404", "Sprawdź ponownie"),
            "brak-karty", new SignError("Nie wykryto karty w czytniku", "Włóż kartę kwalifikowaną chipem do góry i poczekaj, aż dioda czytnika zapali się na zielono.", "SIG-CARD-01", "Spróbuj ponownie"),
            "pin", new SignError("Błędny PIN", "Pozostały 2 próby. Po trzeciej błędnej próbie karta zostanie zablokowana i będzie potrzebny kod PUK.", "SIG-PIN-02", "Wpisz PIN ponownie"),
            "cert", new SignError("Certyfikat na karcie jest nieważny", "Certyfikat wygasł 14.10.2026. Podpis nie jest możliwy. Zgłoś się do Biura Administracyjnego po nowy certyfikat.", "SIG-CERT-EXP", "Zgłoś problem"),
            "polaczenie", new SignError("Przerwano połączenie z aplikacją do podpisu", "Dokument nie został podpisany. Sprawdź, czy aplikacja nadal działa.", "SIG-CONN-499", "Spróbuj ponownie"),
            "weryfikacja", new SignError("Podpis odrzucony przy weryfikacji", "Serwer nie potwierdził ważności podpisu (brak odpowiedzi usługi znacznika czasu). Dokument pozostaje niepodpisany.", "SIG-VER-TSA", "Spróbuj ponownie"));

    @Inject
    private OidcSettings settings;
    @Inject
    private CurrentUser currentUser;
    @Inject
    private SigningService signing;
    @Inject
    private pl.ejtechnology.tk.eSkarga.domain.service.CaseDocumentService documents;
    @Inject
    private pl.ejtechnology.tk.eSkarga.web.ViewData data;
    private Long dokumentId;
    private String rodzajPisma = "ZARZ_BRAKI";
    private String sesja;
    private String skrot;
    private String certyfikaty;
    private String podpis;
    private String algorytmPodpisu = "RSA_SHA256";
    private String stan = "1";
    private int krok = 0;
    private String blad;
    private boolean sklad;
    private boolean podpisano;
    /** Podgląd PDF podpisywanego dokumentu rozwinięty w kroku „Podsumowanie” (widżet pdf.js z DocumentServlet). */
    private boolean podglad;

    public void applyState() {
        String s = stan == null ? "1" : stan;
        krok = Math.max(0, Math.min(3, (s.isEmpty() ? '1' : s.charAt(0)) - '1'));
        blad = ERRORS.keySet().stream().filter(s::contains).findFirst().orElse(null);
        sklad = s.contains("sklad");
        podpisano = krok == 3 && blad == null;
    }

    /** Przejście do następnego kroku; w aplikacji realnej krok 2 i 3 wyzwala LocalSignerClient. */
    public void next() {
        if (krok < 3) {
            krok++;
        }
        if (krok == 3) {
            podpisano = true;
        }
    }

    public void retry() {
        blad = null;
    }
    /** Krok 1 (D10): dane do podpisu dla aplikacji lokalnej – do przeglądarki trafia skrót, nigdy dokument ani klucz. */
    public void begin() {
        if (settings.isDemo() || dokumentId == null) {
            next();
            return;
        }
        try {
            var d = signing.begin(dokumentId, certyfikaty == null ? List.of() : List.of(certyfikaty.split(",")), algorytmPodpisu == null ? "RSA_SHA256" : algorytmPodpisu, currentUser.getLogin());
            sesja = d.sessionId();
            skrot = d.digestBase64();
            krok = 2;
        } catch (RuntimeException e) {
            blad = "weryfikacja";
            FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(FacesMessage.SEVERITY_ERROR, "Nie udało się rozpocząć podpisu.", e.getMessage()));
        }
    }
    /** Krok 2: wartość podpisu z karty (aplikacja lokalna) → złożenie, status czynności i ruch procesu w jednej transakcji. */
    public void complete() {
        if (settings.isDemo() || dokumentId == null) {
            next();
            return;
        }
        try {
            signing.complete(dokumentId, sesja, podpis, !sklad, LetterKind.valueOf(rodzajPisma), currentUser.getLogin());
            krok = 3;
            podpisano = true;
            blad = null;
        } catch (RuntimeException e) {
            blad = "weryfikacja";
            FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(FacesMessage.SEVERITY_ERROR, "Podpis odrzucony.", e.getMessage()));
        }
    }
    public Long getDokumentId() { return dokumentId; }
    public void setDokumentId(Long id) { this.dokumentId = id; }
    public String getRodzajPisma() { return rodzajPisma; }
    public void setRodzajPisma(String r) { this.rodzajPisma = r; }
    public String getSesja() { return sesja; }
    public String getSkrot() { return skrot; }
    public String getCertyfikaty() { return certyfikaty; }
    public void setCertyfikaty(String c) { this.certyfikaty = c; }
    public String getPodpis() { return podpis; }
    public String getAlgorytmPodpisu() { return algorytmPodpisu; }
    public void setAlgorytmPodpisu(String a) { this.algorytmPodpisu = a; }
    public void setPodpis(String p) { this.podpis = p; }

    public SignError getError() {
        return blad == null ? null : ERRORS.get(blad);
    }

    public boolean isHasError() {
        return blad != null;
    }

    public String stepClass(int i) {
        if (i < krok) {
            return "done";
        }
        return i == krok && blad != null ? "bad" : "";
    }

    public String getNextLabel() {
        return switch (krok) {
            case 0 -> "Dalej: połącz z aplikacją";
            case 1 -> "Dalej: podpisz kartą";
            case 2 -> "Czekam na aplikację…";
            default -> "Zamknij i wróć do sprawy";
        };
    }

    public String getCancelLabel() {
        return krok == 3 && blad == null ? "Podpisz kolejny dokument" : "Anuluj";
    }

    /** Dokument do podpisu (poza demo z parametru ?dokument=); null w demo albo bez parametru. */
    private pl.ejtechnology.tk.eSkarga.domain.model.CaseDocument dokument() {
        if (settings.isDemo() || dokumentId == null) {
            return null;
        }
        try {
            return documents.require(dokumentId);
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** Sygnatura sprawy podpisywanego dokumentu – do odnośnika „Zamknij i wróć do sprawy”; w demo dane przykładowe. */
    public String getSygnatura() {
        var d = dokument();
        return d == null ? (sklad ? "Ts 276/26" : "Ts 318/26") : d.getCaseFile().getDocket().format();
    }

    /** Sygnatura jako {@code DocketNumber} dla {@code tk:sig}. */
    public pl.ejtechnology.tk.eSkarga.domain.DocketNumber getSygnaturaDn() {
        return pl.ejtechnology.tk.eSkarga.domain.DocketNumber.parse(getSygnatura());
    }

    /** Tytuł dokumentu z akt; w demo – makieta. */
    public String getDocTitle() {
        var d = dokument();
        return d != null ? d.getTitle() : (sklad ? "Postanowienie o odmowie nadania dalszego biegu" : "Zarządzenie o wyznaczeniu sędziego");
    }

    /** Karty dokumentu w aktach („k. 15”) albo „—”, gdy jeszcze niepaginowany. */
    public String getDocPages() {
        var d = dokument();
        return d != null ? d.pagesLabel() : "1 strona";
    }

    /** Adres PDF w DocumentServlet (autoryzacja sesji, Range, no-store) – null w demo i bez pliku. */
    public String getDocUrl() {
        var d = dokument();
        return d == null || d.getStorageKey() == null || d.isClassified() ? null : "/akta/dokument/" + d.getId();
    }

    public boolean isPodglad() { return podglad; }
    public void togglePodglad() { podglad = !podglad; }

    /** Skarżący/wnioskodawca sprawy podpisywanego dokumentu – z danych widoku (po filtrze dostępu D4). */
    public String getStrona() {
        var d = dokument();
        if (d == null) {
            return sklad ? "Jan Przykładowy" : "Przykładowa Spółka z o.o.";
        }
        var cs = data.caseBy(getSygnatura());
        return cs == null || cs.skarzacy() == null ? "" : cs.skarzacy();
    }

    /** „Podpisujesz jako”: nazwa z ID-users; sędzia działający za Prezesa (BR-10) ma to zaznaczone. */
    public String getPodpisujacy() {
        if (dokument() == null) {
            return "sędzia TK Maria Makietowa — wykonująca obowiązki Prezesa TK (upoważnienie z 30.10.2026)";
        }
        String kto = currentUser.getDisplayName() == null || currentUser.getDisplayName().isBlank() ? currentUser.getLogin() : currentUser.getDisplayName();
        return currentUser.isActingAsPresident() ? kto + " — wykonujący obowiązki Prezesa TK (upoważnienie, BR-10)" : kto;
    }

    public String getDocShort() {
        var d = dokument();
        if (d != null) {
            return getSygnatura() + " · " + d.getTitle();
        }
        return sklad ? "Ts 276/26 · postanowienie" : "Ts 318/26 · zarządzenie";
    }

    public List<Effect> getSkutki() {
        if (dokument() != null && rodzajPisma != null) {
            LetterKind kind = LetterKind.valueOf(rodzajPisma);
            String syg = getSygnatura();
            return switch (kind) {
                case ZARZ_WYZNACZENIE_SEDZIEGO -> List.of(Effect.next("Sędzia wskazany w zarządzeniu zostanie wyznaczony do wstępnej kontroli " + syg + " (przydział, zadanie w procesie)."),
                        Effect.next("Status sprawy zmieni się na „We wstępnej kontroli”."), Effect.warning("Podpisu nie można cofnąć. Zmiana wymaga nowego zarządzenia."));
                case ZARZ_BRAKI -> List.of(Effect.next("Zarządzenie trafi do ekspedycji; termin na usunięcie braków zacznie biec od doręczenia."),
                        Effect.next("Status sprawy: „Wezwanie do usunięcia braków”."), Effect.warning("Podpisu nie można cofnąć."));
                case POST_ODMOWA -> List.of(Effect.next("Postanowienie trafi do ekspedycji; termin na zażalenie zacznie biec od doręczenia."),
                        Effect.next("Status sprawy: „Odmowa – termin na zażalenie”."), Effect.warning("Podpisu nie można cofnąć."));
                default -> List.of(Effect.next(kind.title() + " w sprawie " + syg + " stanie się czynnością podpisaną."), Effect.warning("Podpisu nie można cofnąć."));
            };
        }
        return List.of(Effect.next("Sędzia TK Adam Testowy zostanie wyznaczony do wstępnej kontroli Ts 318/26."),
                Effect.next("Status zmieni się na „We wstępnej kontroli”. Zawiadomienie trafi do r.pr. Oleńki Testowej."),
                Effect.warning("Podpisu nie można cofnąć. Zmiana wymaga nowego zarządzenia."));
    }

    public String getStan() { return stan; }
    public void setStan(String stan) { this.stan = stan; }
    public int getKrok() { return krok; }
    public boolean isSklad() { return sklad; }
    public boolean isPodpisano() { return podpisano; }
    public boolean isWaiting() { return krok == 2 && blad == null; }
}
