package pl.ejtechnology.tk.eSkarga.domain.service;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import jakarta.ejb.Stateless;
import jakarta.ejb.TransactionAttribute;
import jakarta.ejb.TransactionAttributeType;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import pl.ejtechnology.tk.eSkarga.audit.AuditService;
import pl.ejtechnology.tk.eSkarga.security.AppRole;
import pl.ejtechnology.tk.eSkarga.security.RequiresRole;
import pl.ejtechnology.tk.eSkarga.domain.CaseStatus;
import pl.ejtechnology.tk.eSkarga.domain.DocketNumber;
import pl.ejtechnology.tk.eSkarga.domain.DocketSymbol;
import pl.ejtechnology.tk.eSkarga.domain.DocumentKind;
import pl.ejtechnology.tk.eSkarga.domain.LetterQualification;
import pl.ejtechnology.tk.eSkarga.domain.docket.DocketNumberService;
import pl.ejtechnology.tk.eSkarga.domain.model.AssignmentRole;
import pl.ejtechnology.tk.eSkarga.domain.model.CaseAssignment;
import pl.ejtechnology.tk.eSkarga.domain.model.CaseDocument;
import pl.ejtechnology.tk.eSkarga.domain.model.CaseFile;
import pl.ejtechnology.tk.eSkarga.domain.model.CaseFileVersion;
import pl.ejtechnology.tk.eSkarga.domain.model.ClosingMode;
import pl.ejtechnology.tk.eSkarga.domain.model.ActStatus;
import pl.ejtechnology.tk.eSkarga.domain.model.ProceduralAct;
import pl.ejtechnology.tk.eSkarga.domain.model.ProceduralActKind;
import pl.ejtechnology.tk.eSkarga.domain.model.FinalRuling;
import pl.ejtechnology.tk.eSkarga.domain.model.IncomingLetter;
import pl.ejtechnology.tk.eSkarga.domain.model.IncomingScan;
import pl.ejtechnology.tk.eSkarga.documents.DocumentStore;
import pl.ejtechnology.tk.eSkarga.domain.model.Party;
import pl.ejtechnology.tk.eSkarga.domain.model.PartyRole;
import pl.ejtechnology.tk.eSkarga.domain.model.PowerOfAttorney;
import pl.ejtechnology.tk.eSkarga.domain.model.PowerOfAttorneyKind;
import pl.ejtechnology.tk.eSkarga.domain.model.Profession;
import pl.ejtechnology.tk.eSkarga.domain.model.ReviewSubject;
import pl.ejtechnology.tk.eSkarga.process.PreliminaryReviewProcess;
import pl.ejtechnology.tk.eSkarga.process.ProcessVariables;
import pl.ejtechnology.tk.eSkarga.web.AppClock;

/**
 * Założenie sprawy i dołączanie pism (T-013, UC-03, UC-04). Zatwierdzenie wpisu to jedna transakcja JTA (D1):
 * nadanie sygnatury (BR-01) → wpis z wersją 1 → strony, przedmiot, orzeczenie → karty akt → start procesu Operaton
 * z kluczem biznesowym = sygnatura. Błąd startu procesu wycofuje wpis i nie zużywa numeru.
 */
@Stateless
@RequiresRole   // domyślnie: dowolna rola eSkargi (odczyty); zmiany stanu – reguły na metodach
public class CaseFileService {

    /** Strona z karty sprawy (krok 1 S-07). */
    public record PartyData(PartyRole role, String displayName, String deliveryAddress, String eDeliveryAddress,
                            PowerOfAttorneyKind poaKind, Profession profession, String registryNo, LocalDate poaGrantedOn) {
    }

    /** Karta sprawy (S-07). */
    public record CreateCaseCommand(DocketSymbol symbol, long letterId, List<PartyData> parties, String reviewAct, String reviewUnit,
                                    String reviewStandard, String rulingAuthority, String rulingRef, LocalDate rulingIssuedOn,
                                    LocalDate rulingDeliveredOn, boolean extraordinaryRemedy, boolean urgent, boolean anonymise,
                                    int letterPages, String userId) {
    }

    @PersistenceContext(unitName = "repertorium")
    EntityManager em;
    @Inject
    DocketNumberService docketNumbers;
    @Inject
    PreliminaryReviewProcess process;
    @Inject
    AuditService audit;
    @Inject
    AppClock clock;
    @Inject
    DocumentStore store;
    @Inject
    pl.ejtechnology.tk.eSkarga.search.SearchService search;

    @TransactionAttribute(TransactionAttributeType.REQUIRED)
    /**
     * Skany z rejestracji wpływu (S-03) stają się dokumentami akt (UC-03): pierwszy PDF to plik pisma procesowego, kolejne PDF-y – osobne
     * dokumenty (tytuł = nazwa pliku) na kolejnych kartach; TIFF-y zostają w repozytorium wpływu (akta przyjmują PDF, T-055). Plik jest
     * kopiowany pod klucz akt ({@code akta/<id>/…}), skrót SHA-256 bez zmian.
     */
    private void attachIntakeScans(CaseFile cf, CaseDocument pismo, IncomingLetter letter, String userId) {
        if (store == null) {
            return;
        }
        List<IncomingScan> scans = em.createQuery("select s from IncomingScan s where s.letter = :l order by s.id", IncomingScan.class).setParameter("l", letter).getResultList();
        boolean first = true;
        for (IncomingScan scan : scans) {
            // PDF od razu kartą akt; DOC/DOCX trafia do akt jako oryginał i czeka na konwersję do PDF/A przez workera (ADR 0026); TIFF zostaje skanem wpływu
            if (!pl.ejtechnology.tk.eSkarga.documents.FileTypes.isCaseDocument(scan.getContentType())) {
                continue;
            }
            byte[] bytes;
            try (java.io.InputStream in = store.get(scan.getStorageKey(), Optional.empty())) {
                bytes = in.readAllBytes();
            } catch (java.io.IOException e) {
                throw new IllegalStateException("Nie udało się odczytać skanu " + scan.getFileName() + " z repozytorium", e);
            }
            boolean office = pl.ejtechnology.tk.eSkarga.documents.FileTypes.isOffice(scan.getContentType());
            String key = "akta/" + cf.getId() + "/" + java.util.UUID.randomUUID() + "." + pl.ejtechnology.tk.eSkarga.documents.FileTypes.extension(scan.getContentType());
            DocumentStore.Stored stored = store.put(key, new java.io.ByteArrayInputStream(bytes), bytes.length, scan.getContentType(), false);
            if (first) {
                pismo.attachFile(stored.key(), stored.sha256());
                if (office) {
                    pismo.markSource(scan.getContentType(), scan.getFileName());
                }
                first = false;
            } else {
                CaseDocument extra = new CaseDocument(cf, scan.getFileName(), DocumentKind.ZALACZNIK, pismo.getDocumentDate(), userId);
                int last = lastPage(cf);
                extra.paginate(1, last + 1, last + 1);
                extra.attachFile(stored.key(), stored.sha256());
                if (office) {
                    extra.markSource(scan.getContentType(), scan.getFileName());
                }
                em.persist(extra);
            }
        }
    }

    @RequiresRole({AppRole.TK_REFERENT})
    public CaseFile createCase(CreateCaseCommand cmd) {
        IncomingLetter letter = em.find(IncomingLetter.class, cmd.letterId());
        if (letter == null) {
            throw new IllegalArgumentException("Brak pisma " + cmd.letterId());
        }
        if (letter.getQualification() != null) {
            throw new IllegalStateException("Pismo " + letter.getRegisterNo() + " jest już zakwalifikowane");
        }
        LocalDate today = clock.today();
        DocketNumber docket = docketNumbers.nextNumber(cmd.symbol(), today);
        CaseFile cf = new CaseFile(docket, today, cmd.userId());
        CaseFileVersion v1 = cf.addVersion(new CaseFileVersion(cf, 1, CaseStatus.ZAREJESTROWANA, cmd.userId()));
        v1.setReviewSubject(cmd.reviewAct() + (cmd.reviewUnit() == null ? "" : " " + cmd.reviewUnit()));
        v1.setReviewStandard(cmd.reviewStandard());
        v1.setFlagUrgent(cmd.urgent());
        v1.setFlagAnonymise(cmd.anonymise());
        em.persist(cf);
        for (PartyData p : cmd.parties()) {
            Party party = new Party(cf, p.role(), p.displayName(), p.deliveryAddress(), p.eDeliveryAddress());
            em.persist(party);
            if (p.poaKind() != null) {
                em.persist(new PowerOfAttorney(party, p.poaKind(), p.profession(), p.registryNo(), p.poaGrantedOn(), null));
            }
        }
        em.persist(new ReviewSubject(cf, cmd.reviewAct(), cmd.reviewUnit(), cmd.reviewStandard()));
        if (cmd.rulingAuthority() != null) {
            em.persist(new FinalRuling(cf, cmd.rulingAuthority(), cmd.rulingRef(), cmd.rulingIssuedOn(), cmd.rulingDeliveredOn(), cmd.extraordinaryRemedy()));
        }
        CaseDocument pismo = new CaseDocument(cf, cmd.symbol() == DocketSymbol.TS ? "Skarga konstytucyjna" : "Wniosek", DocumentKind.PISMO_PROCESOWE,
                letter.getFiledOn() == null ? today : letter.getFiledOn(), cmd.userId());
        pismo.paginate(1, 1, Math.max(1, cmd.letterPages()));
        em.persist(pismo);
        attachIntakeScans(cf, pismo, letter, cmd.userId());
        letter.qualify(cmd.symbol() == DocketSymbol.TS ? LetterQualification.NOWA_SKARGA_TS : LetterQualification.NOWY_WNIOSEK_TW, cf);
        em.persist(new CaseAssignment(cf, cmd.userId(), AssignmentRole.CLERK, today));
        // start procesu w tej samej transakcji (D1); wyjątek wycofuje wpis i licznik sygnatur
        var pi = process.start(cf.getId() == null ? 0L : cf.getId(), docket,
                cmd.symbol() == DocketSymbol.TS ? ProcessVariables.RodzajPisma.SKARGA : ProcessVariables.RodzajPisma.WNIOSEK);
        cf.setProcessInstanceId(pi.getId());
        audit.record(cmd.userId(), "TK_REFERENT", "założenie sprawy", letter.getRegisterNo(), docket.format(), null);
        if (search != null) {
            search.indexCase(cf);
            search.indexDocument(pismo, null);
        }
        return cf;
    }

    /** Pismo do sprawy w toku (UC-04): kolejne karty akt i komunikat do procesu (uzupełnienie braków albo zażalenie). */
    @TransactionAttribute(TransactionAttributeType.REQUIRED)
    @RequiresRole({AppRole.TK_REFERENT, AppRole.TK_BIURO_PODAWCZE})
    public CaseDocument attachLetter(long caseId, long letterId, String title, int pages, boolean appeal, String userId) {
        CaseFile cf = require(caseId);
        if (cf.current().getStatus().isReadOnly()) {
            throw new IllegalStateException("Sprawa " + cf.getDocket().format() + " jest tylko do odczytu (BR-08)");
        }
        IncomingLetter letter = em.find(IncomingLetter.class, letterId);
        CaseDocument doc = new CaseDocument(cf, title, DocumentKind.PISMO_PROCESOWE, letter.getFiledOn(), userId);
        int lastPage = lastPage(cf);
        doc.paginate(1, lastPage + 1, lastPage + Math.max(1, pages));
        em.persist(doc);
        letter.qualify(LetterQualification.PISMO_DO_SPRAWY, cf);
        if (!appeal) {
            process.supplementReceived(cf.getDocket());
        }
        if (search != null) {
            search.indexDocument(doc, null);
        }
        audit.record(userId, "TK_REFERENT", appeal ? "dołączenie zażalenia" : "dołączenie pisma do akt", letter.getRegisterNo(), cf.getDocket().format(), null);
        return doc;
    }

    /** Zmiana statusu = nowa wersja wpisu (NFR-01). */
    @TransactionAttribute(TransactionAttributeType.REQUIRED)
    @RequiresRole
    public CaseFileVersion changeStatus(long caseId, CaseStatus status, String userId) {
        return changeStatus(caseId, status, userId, null);
    }

    /**
     * Nowa wersja wpisu z dodatkowymi polami ustawionymi PRZED zapisem ({@code init}): wersje są tylko dopisywane (NFR-01, rola
     * aplikacji bez UPDATE na {@code case_file_version}), więc zmiana po {@code persist} kończyłaby się odmową bazy.
     */
    @TransactionAttribute(TransactionAttributeType.REQUIRED)
    @RequiresRole
    public CaseFileVersion changeStatus(long caseId, CaseStatus status, String userId, java.util.function.Consumer<CaseFileVersion> init) {
        CaseFile cf = require(caseId);
        CaseFileVersion v = cf.current().next(status, userId);
        reNumber(cf, v);
        if (init != null) {
            init.accept(v);
        }
        em.persist(cf.addVersion(v));
        audit.record(userId, null, "zmiana statusu: " + status, null, cf.getDocket().format(), null);
        return v;
    }

    /**
     * Zakończenie sprawy Ts/Tw po przekazaniu do repertorium SK/K/U (UC-12, FR-19): nowa wersja ze statusem
     * „zakończona – nadano bieg”, sposobem zakończenia i sygnaturą powiązaną; dalsze zmiany blokuje BR-08.
     */
    @TransactionAttribute(TransactionAttributeType.REQUIRED)
    @RequiresRole(system = true)
    public CaseFileVersion markTransferred(long caseId, String relatedDocket, String userId) {
        CaseFile cf = require(caseId);
        if (cf.current().getStatus().isReadOnly()) {
            return cf.current();
        }
        CaseFileVersion v = cf.current().next(CaseStatus.ZAKONCZONA_NADANO_BIEG, userId);
        reNumber(cf, v);
        v.setClosingMode(ClosingMode.NADANIE_BIEGU);
        v.setClosedOn(clock.today());
        v.setRelatedDocket(relatedDocket);
        em.persist(cf.addVersion(v));
        em.persist(new ProceduralAct(cf, ProceduralActKind.PRZEKAZANIE, "PRZEKAZANIE_SK_K_U", clock.instant(), userId, "art. 61 ust. 1 (nadanie dalszego biegu)", ActStatus.PODPISANA));
        audit.record(userId, null, "przekazanie do rozpoznania merytorycznego: " + relatedDocket, null, cf.getDocket().format(), null);
        return v;
    }

    static void reNumber(CaseFile cf, CaseFileVersion v) {
        // next() nadaje version+1 od wersji bieżącej; po odrzuconych korektach numer musi być wyższy od wszystkich
        int wanted = cf.nextVersionNo();
        if (v.getVersion() != wanted) {
            try {
                var f = CaseFileVersion.class.getDeclaredField("version");
                f.setAccessible(true);
                f.setInt(v, wanted);
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException(e);
            }
        }
    }

    public CaseFile require(long caseId) {
        CaseFile cf = em.find(CaseFile.class, caseId);
        if (cf == null) {
            throw new IllegalArgumentException("Brak sprawy " + caseId);
        }
        return cf;
    }

    @RequiresRole(workers = true)
    public Optional<CaseFile> byDocket(DocketNumber docket) {
        return em.createQuery("select c from CaseFile c where c.docketSymbol = :s and c.docketNumber = :n and c.docketYear = :y", CaseFile.class)
                .setParameter("s", docket.symbol().code()).setParameter("n", docket.number()).setParameter("y", docket.year())
                .getResultStream().findFirst().map(CaseFileService::withVersions);
    }

    public List<CaseFile> inDocket(DocketSymbol symbol, int year) {
        return withVersions(em.createQuery("select c from CaseFile c where c.docketSymbol = :s and c.docketYear = :y order by c.docketNumber desc", CaseFile.class)
                .setParameter("s", symbol.code()).setParameter("y", year).getResultList());
    }

    /**
     * Wersje wpisu ładowane w transakcji usługi: wołający (beany widoków, serwlety) czyta {@link CaseFile#current()} już po jej
     * zakończeniu, a leniwa kolekcja bez sesji kończyła się {@code LazyInitializationException} (repertorium S-08, akta S-14).
     */
    private static CaseFile withVersions(CaseFile cf) {
        org.hibernate.Hibernate.initialize(cf.getVersions());
        return cf;
    }

    private static List<CaseFile> withVersions(List<CaseFile> list) {
        list.forEach(CaseFileService::withVersions);
        return list;
    }

    public List<CaseDocument> documents(CaseFile cf) {
        return em.createQuery("select d from CaseDocument d where d.caseFile = :c order by d.volumeNo, d.pageFrom, d.id", CaseDocument.class)
                .setParameter("c", cf).getResultList();
    }

    @RequiresRole(workers = true)
    public List<Party> parties(CaseFile cf) {
        return em.createQuery("select p from Party p where p.caseFile = :c order by p.id", Party.class).setParameter("c", cf).getResultList();
    }

    public int lastPage(CaseFile cf) {
        Integer max = em.createQuery("select max(d.pageTo) from CaseDocument d where d.caseFile = :c and d.internalNote = false", Integer.class)
                .setParameter("c", cf).getSingleResult();
        return max == null ? 0 : max;
    }

    /** Wyszukiwanie proste (S-02 bez OpenSearch): sygnatura, strona, przedmiot, wzorzec – bez filtru dostępu (nakłada go wołający). */
    public List<CaseFile> search(String query, int limit) {
        String q = query == null ? "" : query.trim();
        if (q.isEmpty()) {
            return List.of();
        }
        try {
            DocketNumber dn = DocketNumber.parse(q);
            return byDocket(dn).map(List::of).orElse(List.of());
        } catch (IllegalArgumentException notADocket) {
            String like = "%" + q.toLowerCase() + "%";
            return withVersions(em.createQuery("select distinct c from CaseFile c left join Party p on p.caseFile = c left join CaseFileVersion v on v.caseFile = c "
                            + "where lower(p.displayName) like :q or lower(v.reviewSubject) like :q or lower(v.reviewStandard) like :q order by c.id desc", CaseFile.class)
                    .setParameter("q", like).setMaxResults(limit).getResultList());
        }
    }

    /** Podpowiedzi powiązań (FR-05): ten sam skarżący/pełnomocnik albo przepis. */
    public List<CaseFile> related(String partyName, String reviewAct) {
        return withVersions(em.createQuery("select distinct p.caseFile from Party p where lower(p.displayName) = lower(:n)", CaseFile.class)
                .setParameter("n", partyName).getResultList());
    }
}
