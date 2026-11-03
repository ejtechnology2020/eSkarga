package pl.ejtechnology.tk.eSkarga.docs.api;

import java.io.File;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;

import pl.ejtechnology.tk.eSkarga.common.security.Settings;
import pl.ejtechnology.tk.eSkarga.docs.hsm.AuditChainSigner;
import pl.ejtechnology.tk.eSkarga.docs.hsm.HsmKeyStore;
import pl.ejtechnology.tk.eSkarga.docs.ocr.OcrService;
import pl.ejtechnology.tk.eSkarga.docs.pdf.OpenHtmlToPdfRenderer;
import pl.ejtechnology.tk.eSkarga.docs.pdf.PdfAValidator;
import pl.ejtechnology.tk.eSkarga.docs.pdf.PdfRenderer;
import pl.ejtechnology.tk.eSkarga.docs.pdf.PdfWatermark;
import pl.ejtechnology.tk.eSkarga.docs.pdf.VeraPdfValidator;
import pl.ejtechnology.tk.eSkarga.docs.redaction.PdfRedactor;
import pl.ejtechnology.tk.eSkarga.docs.signature.SignatureValidationService;
import pl.ejtechnology.tk.eSkarga.docs.signature.TimestampSources;
import pl.ejtechnology.tk.eSkarga.docs.signature.TwoStepPadesSigner;
import pl.ejtechnology.tk.eSkarga.docs.templates.FreemarkerTemplateRenderer;
import pl.ejtechnology.tk.eSkarga.docs.templates.TemplateRenderer;

/** Jedna instancja każdego komponentu usługi; konfiguracja z właściwości ({@code eskarga.docs.*}, {@code eskarga.hsm.*}). */
@ApplicationScoped
public class DocsServices {

    private TemplateRenderer templates;
    private PdfRenderer pdf;
    private PdfAValidator pdfa;
    private PdfWatermark watermark;
    private OcrService ocr;
    private SignatureValidationService signatures;
    private TwoStepPadesSigner signer;
    private PdfRedactor redactor;
    private KeyStore hsm;
    private AuditChainSigner auditSigner;

    @PostConstruct
    void init() {
        File fontDir = new File(Settings.value("eskarga.pdf.fontDir", "/usr/share/fonts/truetype/liberation"));
        templates = new FreemarkerTemplateRenderer();
        pdf = new OpenHtmlToPdfRenderer(fontDir);
        pdfa = new VeraPdfValidator();
        watermark = new PdfWatermark(fontDir);
        ocr = OcrService.detect();
        redactor = new PdfRedactor();
        signer = new TwoStepPadesSigner(TimestampSources.fromSettings());
        List<X509Certificate> anchors = new ArrayList<>();
        String trustStore = Settings.value("eskarga.docs.trustStore", "");
        if (!trustStore.isBlank()) {
            try {
                KeyStore ts = KeyStore.getInstance("PKCS12");
                try (var in = new java.io.FileInputStream(trustStore)) {
                    ts.load(in, Settings.value("eskarga.docs.trustStorePassword", "").toCharArray());
                }
                for (Enumeration<String> e = ts.aliases(); e.hasMoreElements();) {
                    Certificate c = ts.getCertificate(e.nextElement());
                    if (c instanceof X509Certificate x) {
                        anchors.add(x);
                    }
                }
            } catch (Exception e) {
                throw new IllegalStateException("Nie udało się wczytać zaufanych certyfikatów", e);
            }
        }
        signatures = new SignatureValidationService(anchors);
        if (!"off".equalsIgnoreCase(Settings.value("eskarga.hsm.mode", "off"))) {
            HsmKeyStore.Config cfg = HsmKeyStore.Config.fromSettings();
            hsm = HsmKeyStore.open(cfg);
            auditSigner = new AuditChainSigner(hsm, Settings.value("eskarga.hsm.auditKeyAlias", "audyt"), cfg.pin());
        }
    }

    public TemplateRenderer templates() { return templates; }
    public PdfRenderer pdf() { return pdf; }
    public PdfAValidator pdfa() { return pdfa; }
    public PdfWatermark watermark() { return watermark; }
    public OcrService ocr() { return ocr; }
    public SignatureValidationService signatures() { return signatures; }
    public TwoStepPadesSigner signer() { return signer; }
    public PdfRedactor redactor() { return redactor; }
    public KeyStore hsm() { return hsm; }
    public AuditChainSigner auditSigner() {
        if (auditSigner == null) {
            throw new IllegalStateException("HSM nie jest skonfigurowany (eskarga.hsm.mode)");
        }
        return auditSigner;
    }
}
