package pl.ejtechnology.tk.eSkarga.web.view;

import java.io.Serializable;

import jakarta.faces.view.ViewScoped;
import jakarta.inject.Named;

/** S-05 Etykieta wpływu (wydruk A7 albo naklejka 70 × 50 mm, czarno-biała, kod kreskowy Code 128). */
@Named("etykieta")
@ViewScoped
public class EtykietaBean implements Serializable {

    private String nr = "DW 2026/04812";
    private String format = "A7";
    private String drukarka = "BP-1";
    private int kopie = 1;

    public String getNr() { return nr; }
    public void setNr(String nr) { this.nr = nr; }
    public String getFormat() { return format; }
    public void setFormat(String format) { this.format = format; }
    public String getDrukarka() { return drukarka; }
    public void setDrukarka(String drukarka) { this.drukarka = drukarka; }
    public int getKopie() { return kopie; }
    public void setKopie(int kopie) { this.kopie = kopie; }

    /** Treść kodu kreskowego bez spacji i znaków specjalnych. */
    public String getBarcodeText() {
        return nr.replaceAll("[^A-Za-z0-9]", "");
    }
}
