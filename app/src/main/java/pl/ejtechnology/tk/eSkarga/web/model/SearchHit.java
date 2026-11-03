package pl.ejtechnology.tk.eSkarga.web.model;

import java.io.Serializable;
import java.time.LocalDate;

import pl.ejtechnology.tk.eSkarga.domain.DocketNumber;

/** Trafienie w tekście akt (S-02, UC-20). Fragment zawiera znaczniki &lt;mark&gt; wokół trafienia. */
public record SearchHit(String dokument, String href, DocketNumber sprawa, String karta, LocalDate data, String rodzaj,
                        String fragment, String uwaga) implements Serializable {
}
