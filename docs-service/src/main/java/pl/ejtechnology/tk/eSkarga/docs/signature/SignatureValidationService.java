package pl.ejtechnology.tk.eSkarga.docs.signature;

import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import eu.europa.esig.dss.enumerations.Indication;
import eu.europa.esig.dss.model.DSSDocument;
import eu.europa.esig.dss.model.InMemoryDocument;
import eu.europa.esig.dss.model.x509.CertificateToken;
import eu.europa.esig.dss.simplereport.SimpleReport;
import eu.europa.esig.dss.spi.validation.CommonCertificateVerifier;
import eu.europa.esig.dss.spi.x509.CommonTrustedCertificateSource;
import eu.europa.esig.dss.validation.SignedDocumentValidator;
import eu.europa.esig.dss.validation.reports.Reports;

/**
 * Walidacja podpisów elektronicznych wpływających dokumentów (EU DSS; PAdES/XAdES/CAdES) – T-031, NFR-11.
 * Zaufane certyfikaty (lista TSL / kotwice TK) wstrzykuje konfiguracja; bez nich wynik podpisu to INDETERMINATE
 * i dokument dostaje ostrzeżenie, nigdy nie jest odrzucany automatycznie (decyduje referent).
 */
public final class SignatureValidationService {

    private final CommonTrustedCertificateSource trusted = new CommonTrustedCertificateSource();

    public SignatureValidationService(Collection<X509Certificate> trustAnchors) {
        for (X509Certificate c : trustAnchors) {
            trusted.addCertificate(new CertificateToken(c));
        }
    }

    public SignatureReport validate(byte[] pdfOrSignedFile) {
        DSSDocument doc = new InMemoryDocument(pdfOrSignedFile);
        SignedDocumentValidator validator;
        try {
            validator = SignedDocumentValidator.fromDocument(doc);
        } catch (RuntimeException notSupported) {
            return SignatureReport.none();
        }
        CommonCertificateVerifier verifier = new CommonCertificateVerifier();
        verifier.setTrustedCertSources(trusted);
        verifier.setCheckRevocationForUntrustedChains(false);
        validator.setCertificateVerifier(verifier);
        Reports reports = validator.validateDocument();
        SimpleReport simple = reports.getSimpleReport();
        List<SignatureReport.Signature> out = new ArrayList<>();
        boolean allValid = true;
        for (String id : simple.getSignatureIdList()) {
            Indication ind = simple.getIndication(id);
            boolean valid = ind == Indication.TOTAL_PASSED;
            allValid &= valid;
            out.add(new SignatureReport.Signature(simple.getSignedBy(id), simple.getSigningTime(id) == null ? null : simple.getSigningTime(id).toInstant().toString(),
                    simple.getSignatureFormat(id) == null ? null : simple.getSignatureFormat(id).toString(), ind.name(),
                    simple.getSubIndication(id) == null ? null : simple.getSubIndication(id).name(), valid));
        }
        return new SignatureReport(!out.isEmpty(), allValid, out);
    }
}
