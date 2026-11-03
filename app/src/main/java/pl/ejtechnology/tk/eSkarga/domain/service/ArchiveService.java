package pl.ejtechnology.tk.eSkarga.domain.service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import jakarta.ejb.Stateless;
import jakarta.ejb.TransactionAttribute;
import jakarta.ejb.TransactionAttributeType;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import pl.ejtechnology.tk.eSkarga.audit.AuditService;
import pl.ejtechnology.tk.eSkarga.domain.model.ArchiveCategory;
import pl.ejtechnology.tk.eSkarga.domain.model.ArchiveTransfer;
import pl.ejtechnology.tk.eSkarga.domain.model.CaseDocument;
import pl.ejtechnology.tk.eSkarga.domain.model.CaseFile;
import pl.ejtechnology.tk.eSkarga.domain.model.CaseFileVersion;
import pl.ejtechnology.tk.eSkarga.domain.model.ClosingMode;
import pl.ejtechnology.tk.eSkarga.documents.DocumentStore;
import pl.ejtechnology.tk.eSkarga.web.AppClock;

/**
 * Archiwizacja (T-052, UC-21, FR-34, NFR-12). Kategoria archiwalna i retencja z konfiguracji (ADR 0012). Paczka archiwalna to ZIP:
 * {@code metadata.xml} (metadane sprawy i dokumentów), {@code SHA256SUMS}, pliki PDF/A; zapisywana z Object Lock (compliance).
 * Sprawa z dokumentem bez PDF/A nie może zostać przekazana.
 */
@Stateless
public class ArchiveService {

    /** Wynik kontroli kompletności (S-21). */
    public record Readiness(boolean ready, List<String> missingPdfa, int pages) {
    }

    @PersistenceContext(unitName = "repertorium")
    EntityManager em;
    @Inject
    CaseFileService cases;
    @Inject
    DocumentStore store;
    @Inject
    AuditService audit;
    @Inject
    AppClock clock;

    /** Właściwość {@code eskarga.archive.retention.<SPOSOB>} = {@code KATEGORIA:LATA} (np. {@code B10:10}) albo {@code A} (wieczyście). */
    public static final String RETENTION_PROPERTY_PREFIX = "eskarga.archive.retention.";

    /**
     * Kategoria i okres przechowywania (ADR 0012, decyzja 03.11.2026: konfigurowalny czas): nadpisanie z konfiguracji
     * ({@value #RETENTION_PROPERTY_PREFIX}{@code <SPOSOB>}) ma pierwszeństwo przed tabelą {@code archive_category}, którą
     * administrator edytuje w S-24 ({@link DictionaryService#updateArchiveCategory}).
     */
    public Optional<ArchiveCategory> categoryFor(ClosingMode mode) {
        String override = pl.ejtechnology.tk.eSkarga.common.security.Settings.value(RETENTION_PROPERTY_PREFIX + mode.name(), "");
        if (!override.isBlank()) {
            return Optional.of(parseOverride(mode, override));
        }
        return Optional.ofNullable(em.find(ArchiveCategory.class, mode));
    }

    static ArchiveCategory parseOverride(ClosingMode mode, String value) {
        String v = value.trim();
        if (v.equalsIgnoreCase("A")) {
            return new ArchiveCategory(mode, "A", null);
        }
        String[] parts = v.split(":");
        if (parts.length != 2) {
            throw new IllegalArgumentException("Oczekiwano KATEGORIA:LATA albo A, otrzymano: " + value);
        }
        return new ArchiveCategory(mode, parts[0].trim(), Integer.parseInt(parts[1].trim()));
    }

    public Readiness readiness(CaseFile cf) {
        List<CaseDocument> docs = cases.documents(cf).stream().filter(d -> !d.isInternalNote()).toList();
        List<String> missing = docs.stream().filter(d -> !d.isClassified() && !d.isPdfaCompliant()).map(d -> d.pagesLabel() + " " + d.getTitle()).toList();
        int pages = docs.stream().filter(d -> d.getPageTo() != null).mapToInt(CaseDocument::getPageTo).max().orElse(0);
        return new Readiness(missing.isEmpty(), missing, pages);
    }

    /** Sprawy zamknięte, jeszcze nieprzekazane (S-21). */
    public List<CaseFile> closedNotArchived() {
        return em.createQuery("select v.caseFile from CaseFileVersion v where v.closingMode is not null and v.version = "
                        + "(select max(x.version) from CaseFileVersion x where x.caseFile = v.caseFile) "
                        + "and not exists (select t from ArchiveTransfer t where t.caseFile = v.caseFile) order by v.closedOn", CaseFile.class).getResultList();
    }

    @TransactionAttribute(TransactionAttributeType.REQUIRED)
    public ArchiveTransfer transfer(long caseId, String archivistId) {
        CaseFile cf = cases.require(caseId);
        CaseFileVersion cur = cf.current();
        if (cur.getClosingMode() == null) {
            throw new IllegalStateException("Sprawa " + cf.getDocket().format() + " nie jest zakończona");
        }
        if (cur.getClosingMode() == ClosingMode.NADANIE_BIEGU) {
            throw new IllegalStateException("Akta po nadaniu biegu są w sprawie " + cur.getRelatedDocket() + " – przekazanie po jej zakończeniu");
        }
        Readiness r = readiness(cf);
        if (!r.ready()) {
            throw new IllegalStateException("Brak PDF/A: " + String.join(", ", r.missingPdfa()));
        }
        ArchiveCategory cat = categoryFor(cur.getClosingMode()).orElseThrow(() -> new IllegalStateException("Brak kategorii archiwalnej dla " + cur.getClosingMode()));
        Integer retentionUntil = cat.getRetentionYears() == null ? null : cur.getClosedOn().getYear() + cat.getRetentionYears();
        byte[] pkg = buildPackage(cf, cases.documents(cf), cat, retentionUntil);
        String sha = sha256(pkg);
        String key = "archiwum/" + cf.getDocket().format().replace(' ', '-').replace('/', '-') + "/paczka.zip";
        store.put(key, new ByteArrayInputStream(pkg), pkg.length, "application/zip", true);
        String no = nextTransferNo();
        ArchiveTransfer t = new ArchiveTransfer(no, cf, cat.getCategory(), retentionUntil, r.pages(), key, sha, archivistId);
        em.persist(t);
        audit.record(archivistId, "TK_ARCHIWISTA", "przekazanie do archiwum, spis " + no, key, cf.getDocket().format(), null);
        return t;
    }

    /** Paczka: metadata.xml + SHA256SUMS + dokumenty jawne (bez notatek wewnętrznych i bez treści dokumentów niejawnych). */
    byte[] buildPackage(CaseFile cf, List<CaseDocument> docs, ArchiveCategory cat, Integer retentionUntil) {
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream(); ZipOutputStream zip = new ZipOutputStream(bos, StandardCharsets.UTF_8)) {
            StringBuilder meta = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<akta xmlns=\"urn:tk:eskarga:archiwum:1\">\n");
            meta.append("  <sygnatura>").append(esc(cf.getDocket().format())).append("</sygnatura>\n");
            meta.append("  <dataWpisu>").append(cf.getEnteredOn()).append("</dataWpisu>\n");
            meta.append("  <zakonczenie sposob=\"").append(cf.current().getClosingMode()).append("\">").append(cf.current().getClosedOn()).append("</zakonczenie>\n");
            meta.append("  <kategoria okresDo=\"").append(retentionUntil == null ? "wieczyscie" : retentionUntil).append("\">").append(cat.getCategory()).append("</kategoria>\n");
            StringBuilder sums = new StringBuilder();
            meta.append("  <dokumenty>\n");
            for (CaseDocument d : docs) {
                if (d.isInternalNote()) {
                    continue; // FR-24: notatki wewnętrzne nie trafiają do akt jawnych
                }
                meta.append("    <dokument karty=\"").append(esc(d.pagesLabel())).append("\" rodzaj=\"").append(d.getKind()).append("\" niejawny=\"").append(d.isClassified()).append("\">")
                        .append(esc(d.getTitle())).append("</dokument>\n");
                if (d.getStorageKey() != null) {
                    String name = "dokumenty/" + String.format("%04d", d.getPageFrom() == null ? 0 : d.getPageFrom()) + ".pdf";
                    byte[] content = store.get(d.getStorageKey(), Optional.empty()).readAllBytes();
                    zip.putNextEntry(new ZipEntry(name));
                    zip.write(content);
                    zip.closeEntry();
                    sums.append(sha256(content)).append("  ").append(name).append('\n');
                }
            }
            meta.append("  </dokumenty>\n</akta>\n");
            byte[] metaBytes = meta.toString().getBytes(StandardCharsets.UTF_8);
            zip.putNextEntry(new ZipEntry("metadata.xml"));
            zip.write(metaBytes);
            zip.closeEntry();
            sums.append(sha256(metaBytes)).append("  metadata.xml\n");
            zip.putNextEntry(new ZipEntry("SHA256SUMS"));
            zip.write(sums.toString().getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
            zip.finish();
            return bos.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Nie udało się zbudować paczki archiwalnej", e);
        }
    }

    String nextTransferNo() {
        int year = clock.today().getYear();
        Long count = em.createQuery("select count(t) from ArchiveTransfer t where t.transferNo like :y", Long.class).setParameter("y", "%/" + year).getSingleResult();
        return (count + 1) + "/" + year;
    }

    /** Roczne zamknięcie repertorium: księga jako XHTML do konwersji na PDF/A w usłudze dokumentów (FR-09). */
    public String yearlyLedgerXhtml(String symbol, int year) {
        List<CaseFile> all = em.createQuery("select c from CaseFile c where c.docketSymbol = :s and c.docketYear = :y order by c.docketNumber", CaseFile.class)
                .setParameter("s", symbol).setParameter("y", year).getResultList();
        StringBuilder sb = new StringBuilder("<!DOCTYPE html><html xmlns=\"http://www.w3.org/1999/xhtml\" lang=\"pl\"><head><meta charset=\"UTF-8\"/><title>Repertorium ")
                .append(symbol).append(' ').append(year).append("</title></head><body><h1>Repertorium ").append(symbol).append(' ').append(year)
                .append("</h1><table><thead><tr><th>Sygnatura</th><th>Data wpisu</th><th>Status</th><th>Zakończenie</th></tr></thead><tbody>");
        for (CaseFile c : all) {
            CaseFileVersion v = c.current();
            sb.append("<tr><td>").append(esc(c.getDocket().format())).append("</td><td>").append(c.getEnteredOn()).append("</td><td>")
                    .append(v.getStatus()).append("</td><td>").append(v.getClosingMode() == null ? "" : v.getClosingMode() + " " + v.getClosedOn()).append("</td></tr>");
        }
        return sb.append("</tbody></table></body></html>").toString();
    }

    static String sha256(byte[] data) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    static String esc(String s) {
        return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    public LocalDate today() {
        return clock.today();
    }
}
