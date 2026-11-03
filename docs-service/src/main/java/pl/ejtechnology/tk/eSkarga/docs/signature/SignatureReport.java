package pl.ejtechnology.tk.eSkarga.docs.signature;

import java.util.List;

import jakarta.json.Json;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;

/** Raport walidacji podpisów dokumentu (T-031) zapisywany w metadanych dokumentu akt. */
public record SignatureReport(boolean anySignature, boolean allValid, List<Signature> signatures) {

    /** Jeden podpis: kto, kiedy, format, wynik ETSI (indication/subIndication), ostrzeżenie dla użytkownika. */
    public record Signature(String signedBy, String signingTime, String format, String indication, String subIndication, boolean valid) {
    }

    public static SignatureReport none() {
        return new SignatureReport(false, true, List.of());
    }

    public String warning() {
        if (!anySignature) {
            return null;
        }
        return allValid ? null : "Dokument zawiera podpis, którego nie udało się pozytywnie zweryfikować";
    }

    public JsonObject toJson() {
        JsonArrayBuilder arr = Json.createArrayBuilder();
        for (Signature s : signatures) {
            JsonObjectBuilder b = Json.createObjectBuilder().add("wazny", s.valid()).add("wynik", s.indication());
            if (s.signedBy() != null) {
                b.add("podpisal", s.signedBy());
            }
            if (s.signingTime() != null) {
                b.add("czasPodpisu", s.signingTime());
            }
            if (s.format() != null) {
                b.add("format", s.format());
            }
            if (s.subIndication() != null) {
                b.add("podwynik", s.subIndication());
            }
            arr.add(b);
        }
        JsonObjectBuilder root = Json.createObjectBuilder().add("podpisany", anySignature).add("wszystkieWazne", allValid).add("podpisy", arr);
        if (warning() != null) {
            root.add("ostrzezenie", warning());
        }
        return root.build();
    }
}
