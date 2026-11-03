package pl.ejtechnology.tk.eSkarga.docs.signature;

import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import eu.europa.esig.dss.enumerations.DigestAlgorithm;
import eu.europa.esig.dss.enumerations.SignatureAlgorithm;
import eu.europa.esig.dss.enumerations.SignatureLevel;
import eu.europa.esig.dss.model.DSSDocument;
import eu.europa.esig.dss.model.InMemoryDocument;
import eu.europa.esig.dss.model.SignatureValue;
import eu.europa.esig.dss.model.ToBeSigned;
import eu.europa.esig.dss.model.x509.CertificateToken;
import eu.europa.esig.dss.pades.PAdESSignatureParameters;
import eu.europa.esig.dss.pades.signature.PAdESService;
import eu.europa.esig.dss.spi.DSSUtils;
import eu.europa.esig.dss.spi.validation.CommonCertificateVerifier;
import eu.europa.esig.dss.spi.x509.tsp.TSPSource;

/**
 * Podpis kwalifikowany dwuetapowy (D10, T-040): (1) {@code getDataToSign} → skrót do podpisania kartą przez aplikację lokalną,
 * (2) {@code signDocument} z wartością podpisu + znacznik czasu (PAdES-B-T, gdy skonfigurowano TSA). Parametry z kroku 1
 * są trzymane w sesji podpisu (identyfikator, TTL 10 min), bo oba kroki muszą użyć identycznych parametrów.
 * Kolejne podpisy w składzie 3 sędziów to kolejne sesje na już podpisanym dokumencie.
 */
public final class TwoStepPadesSigner {

    public static final Duration SESSION_TTL = Duration.ofMinutes(10);

    /** Dane kroku 1 dla aplikacji lokalnej. */
    public record DataToSign(String sessionId, byte[] digest, String digestAlgorithm, String signatureAlgorithm) {
        public String digestBase64() {
            return Base64.getEncoder().encodeToString(digest);
        }
    }

    private record Session(DSSDocument document, PAdESSignatureParameters parameters, Instant createdAt, String signatureAlgorithm) {
    }

    private final PAdESService service;
    private final TSPSource tspSource;
    private final Map<String, Session> sessions = new ConcurrentHashMap<>();

    public TwoStepPadesSigner(TSPSource tspSource) {
        this.service = new PAdESService(new CommonCertificateVerifier());
        this.tspSource = tspSource;
        if (tspSource != null) {
            service.setTspSource(tspSource);
        }
    }

    /** Krok 1: parametry PAdES i skrót SHA-256 danych do podpisu; nic nie opuszcza serwera poza skrótem. */
    public DataToSign dataToSign(byte[] pdf, List<X509Certificate> chain, Instant signingTime, String signatureAlgorithm, String reason, String location) {
        if (chain == null || chain.isEmpty()) {
            throw new IllegalArgumentException("Brak certyfikatu podpisującego");
        }
        purgeExpired();
        PAdESSignatureParameters p = new PAdESSignatureParameters();
        p.setSignatureLevel(tspSource == null ? SignatureLevel.PAdES_BASELINE_B : SignatureLevel.PAdES_BASELINE_T);
        p.setDigestAlgorithm(DigestAlgorithm.SHA256);
        p.setSigningCertificate(new CertificateToken(chain.get(0)));
        p.setCertificateChain(chain.stream().map(CertificateToken::new).toList());
        p.bLevel().setSigningDate(Date.from(signingTime));
        p.setReason(reason);
        p.setLocation(location);
        p.setSignerName(chain.get(0).getSubjectX500Principal().getName());
        DSSDocument doc = new InMemoryDocument(pdf);
        ToBeSigned tbs = service.getDataToSign(doc, p);
        byte[] digest = DSSUtils.digest(DigestAlgorithm.SHA256, tbs.getBytes());
        String id = UUID.randomUUID().toString();
        sessions.put(id, new Session(doc, p, Instant.now(), signatureAlgorithm));
        return new DataToSign(id, digest, "SHA-256", signatureAlgorithm);
    }

    /** Krok 2: złożenie podpisu z wartością z karty; sesja jest jednorazowa. */
    public byte[] signDocument(String sessionId, byte[] signatureValue) {
        Session s = sessions.remove(sessionId);
        if (s == null || s.createdAt().plus(SESSION_TTL).isBefore(Instant.now())) {
            throw new IllegalStateException("Sesja podpisu wygasła albo nie istnieje – rozpocznij podpis ponownie");
        }
        SignatureValue value = new SignatureValue(SignatureAlgorithm.valueOf(s.signatureAlgorithm()), signatureValue);
        DSSDocument signed = service.signDocument(s.document(), s.parameters(), value);
        return DSSUtils.toByteArray(signed);
    }

    /** Wygodna ścieżka, gdy aplikacja lokalna jest dostępna po stronie serwera (testy, SoftHSM2). */
    public byte[] sign(byte[] pdf, LocalSignerClient signer, Instant signingTime, String reason, String location) {
        DataToSign d = dataToSign(pdf, signer.certificateChain(), signingTime, signer.signatureAlgorithm(), reason, location);
        return signDocument(d.sessionId(), signer.signDigest(d.digest(), d.digestAlgorithm()));
    }

    public int openSessions() {
        purgeExpired();
        return sessions.size();
    }

    private void purgeExpired() {
        Instant limit = Instant.now().minus(SESSION_TTL);
        sessions.entrySet().removeIf(e -> e.getValue().createdAt().isBefore(limit));
    }
}
