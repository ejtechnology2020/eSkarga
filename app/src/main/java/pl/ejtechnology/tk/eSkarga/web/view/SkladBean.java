package pl.ejtechnology.tk.eSkarga.web.view;

import java.io.Serializable;
import java.time.LocalDate;

import jakarta.faces.view.ViewScoped;
import jakarta.inject.Named;

/**
 * S-18 Skierowanie zażalenia do składu (UC-11, BR-07): dane zażalenia z pomocniczą oceną terminowości, wybór 3 sędziów
 * z wykluczeniem sędziego, który wydał odmowę, termin posiedzenia niejawnego. Zażalenie po terminie – postanowienie
 * o pozostawieniu bez rozpoznania.
 */
@Named("sklad")
@ViewScoped
public class SkladBean implements Serializable {

    private String stan = "w-terminie";
    private String przewodniczacy = "Ewa Wzorcowa";
    private String sprawozdawca = "Piotr Fikcyjny";
    private LocalDate posiedzenie = LocalDate.of(2026, 11, 19);

    public boolean isPoTerminie() { return "po".equals(stan) || "po-terminie".equals(stan); }
    public String getSygnatura() { return isPoTerminie() ? "Ts 240/26" : "Ts 301/26"; }
    public String getSkarzacy() { return isPoTerminie() ? "Lech Zmyślony" : "Robert Fikcyjny"; }
    public String getOdmowa() { return isPoTerminie() ? "postanowienie z 15.08.2026 · sędzia TK Maria Makietowa" : "postanowienie z 21.10.2026 · sędzia TK Adam Testowy"; }
    public String getDoreczenieOdmowy() { return isPoTerminie() ? "20.08.2026" : "28.10.2026 · doręczenie zastępcze"; }
    public String getWniesienie() { return isPoTerminie() ? "02.11.2026 · DW 2026/04806 (ponowne zażalenie)" : "03.11.2026 · DW 2026/04813 · e-Doręczenia"; }
    public String getStan() { return stan; }
    public void setStan(String stan) { this.stan = stan; }
    public String getPrzewodniczacy() { return przewodniczacy; }
    public void setPrzewodniczacy(String p) { this.przewodniczacy = p; }
    public String getSprawozdawca() { return sprawozdawca; }
    public void setSprawozdawca(String s) { this.sprawozdawca = s; }
    public LocalDate getPosiedzenie() { return posiedzenie; }
    public void setPosiedzenie(LocalDate posiedzenie) { this.posiedzenie = posiedzenie; }
}
