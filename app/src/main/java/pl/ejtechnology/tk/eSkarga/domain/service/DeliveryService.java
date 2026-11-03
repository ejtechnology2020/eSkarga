package pl.ejtechnology.tk.eSkarga.domain.service;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.ejb.Stateless;
import jakarta.ejb.TransactionAttribute;
import jakarta.ejb.TransactionAttributeType;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import pl.ejtechnology.tk.eSkarga.audit.AuditService;
import pl.ejtechnology.tk.eSkarga.domain.DeadlineType;
import pl.ejtechnology.tk.eSkarga.domain.DeliveryMethod;
import pl.ejtechnology.tk.eSkarga.domain.deadline.DeadlineCalculator;
import pl.ejtechnology.tk.eSkarga.domain.deadline.DeadlineComputation;
import pl.ejtechnology.tk.eSkarga.domain.model.ActStatus;
import pl.ejtechnology.tk.eSkarga.domain.model.CaseDocument;
import pl.ejtechnology.tk.eSkarga.domain.model.CaseFile;
import pl.ejtechnology.tk.eSkarga.domain.model.Deadline;
import pl.ejtechnology.tk.eSkarga.domain.model.DeadlineStatus;
import pl.ejtechnology.tk.eSkarga.domain.model.Delivery;
import pl.ejtechnology.tk.eSkarga.domain.model.DeliveryChannel;
import pl.ejtechnology.tk.eSkarga.domain.model.Party;
import pl.ejtechnology.tk.eSkarga.domain.model.PartyRole;
import pl.ejtechnology.tk.eSkarga.domain.model.ProceduralAct;
import pl.ejtechnology.tk.eSkarga.process.PreliminaryReviewProcess;
import pl.ejtechnology.tk.eSkarga.process.ProcessVariables;

/**
 * Ekspedycja i rejestr doręczeń (T-041, UC-08, FR-25, FR-26). Adresatem jest pełnomocnik, gdy ustanowiony (BR-06).
 * Zarejestrowanie doręczenia uruchamia termin liczony przez {@link DeadlineCalculator} i ustawia timer procesu (D6);
 * korekta daty doręczenia uchyla stary termin, tworzy nowy i przestawia timer.
 */
@Stateless
public class DeliveryService {

    public static final int AWIZO_DAYS = 14;

    @PersistenceContext(unitName = "repertorium")
    EntityManager em;
    @Inject
    HolidayService holidays;
    @Inject
    PreliminaryReviewProcess process;
    @Inject
    AuditService audit;

    /** Wysyłka dokumentu: adresat wybrany automatycznie (BR-06), klucz idempotencji dla workera (D7). */
    @TransactionAttribute(TransactionAttributeType.REQUIRED)
    public Delivery dispatch(long documentId, DeliveryChannel channel, LocalDate sentOn, String dispatchNo, String userId) {
        CaseDocument doc = em.find(CaseDocument.class, documentId);
        if (doc == null) {
            throw new IllegalArgumentException("Brak dokumentu " + documentId);
        }
        Party addressee = addresseeFor(doc.getCaseFile()).orElseThrow(() -> new IllegalStateException("Sprawa nie ma strony do doręczeń"));
        Delivery d = new Delivery(doc, addressee, channel, "DOR-" + UUID.randomUUID());
        d.dispatched(dispatchNo, sentOn);
        em.persist(d);
        actFor(doc).ifPresent(a -> a.setStatus(ActStatus.WYSLANA));
        audit.record(userId, "TK_REFERENT", "wysyłka: " + channel, doc.getTitle() + " → " + addressee.getDisplayName(), doc.getCaseFile().getDocket().format(), null);
        return d;
    }

    /** Przygotowanie wysyłki elektronicznej: pismo czeka na workera (D7), który potwierdzi nadanie przez API. */
    @TransactionAttribute(TransactionAttributeType.REQUIRED)
    public Delivery prepare(long documentId, DeliveryChannel channel, String userId) {
        CaseDocument doc = em.find(CaseDocument.class, documentId);
        if (doc == null) {
            throw new IllegalArgumentException("Brak dokumentu " + documentId);
        }
        Party addressee = addresseeFor(doc.getCaseFile()).orElseThrow(() -> new IllegalStateException("Sprawa nie ma strony do doręczeń"));
        Delivery d = new Delivery(doc, addressee, channel, "DOR-" + UUID.randomUUID());
        em.persist(d);
        audit.record(userId, "TK_REFERENT", "skierowanie do wysyłki: " + channel, doc.getTitle() + " → " + addressee.getDisplayName(), doc.getCaseFile().getDocket().format(), null);
        return d;
    }

    /** Potwierdzenie nadania przez workera (numer nadania / identyfikator przesyłki); ponowienie z tym samym numerem jest neutralne. */
    @TransactionAttribute(TransactionAttributeType.REQUIRED)
    public Delivery confirmDispatch(long deliveryId, String dispatchNo, LocalDate sentOn, String actor) {
        Delivery d = require(deliveryId);
        if (d.getSentOn() != null) {
            if (dispatchNo != null && !dispatchNo.equals(d.getDispatchNo())) {
                throw new IllegalStateException("Doręczenie " + deliveryId + " ma już inny numer nadania");
            }
            return d;
        }
        d.dispatched(dispatchNo, sentOn);
        actFor(d.getDocument()).ifPresent(a -> a.setStatus(ActStatus.WYSLANA));
        audit.record(actor, "WORKER", "potwierdzenie nadania: " + dispatchNo, d.getDocument().getTitle(), d.getDocument().getCaseFile().getDocket().format(), null);
        return d;
    }

    /** Zwrot przesyłki bez skutku doręczenia – wymaga decyzji referenta o ponownej wysyłce; termin nie rusza. */
    @TransactionAttribute(TransactionAttributeType.REQUIRED)
    public void returned(long deliveryId, LocalDate on, String reason, String actor) {
        Delivery d = require(deliveryId);
        audit.record(actor, "WORKER", "zwrot przesyłki: " + reason + " (" + on + ")", d.getDocument().getTitle(), d.getDocument().getCaseFile().getDocket().format(), null);
    }

    public Delivery require(long deliveryId) {
        Delivery d = em.find(Delivery.class, deliveryId);
        if (d == null) {
            throw new IllegalArgumentException("Brak doręczenia " + deliveryId);
        }
        return d;
    }

    /** BR-06: doręczenia kieruje się do pełnomocnika, jeżeli został ustanowiony; inaczej do skarżącego / wnioskodawcy. */
    public Optional<Party> addresseeFor(CaseFile cf) {
        List<Party> parties = em.createQuery("select p from Party p where p.caseFile = :c order by p.id", Party.class).setParameter("c", cf).getResultList();
        return parties.stream().filter(p -> p.getRole() == PartyRole.PELNOMOCNIK).findFirst()
                .or(() -> parties.stream().filter(p -> p.getRole() == PartyRole.SKARZACY || p.getRole() == PartyRole.WNIOSKODAWCA).findFirst());
    }

    /**
     * Rejestracja zwrotki / UPD (UC-08): data i sposób doręczenia; awizo = skutek doręczenia 14. dnia (ADR 0006 pkt 6).
     * Dla zarządzenia o brakach rusza termin 7 dni (art. 61 ust. 3), dla postanowienia o odmowie – termin na zażalenie (ust. 5).
     */
    @TransactionAttribute(TransactionAttributeType.REQUIRED)
    public Optional<Deadline> registerReceipt(long deliveryId, LocalDate date, DeliveryMethod method, String userId) {
        Delivery d = em.find(Delivery.class, deliveryId);
        if (d == null) {
            throw new IllegalArgumentException("Brak doręczenia " + deliveryId);
        }
        if (d.getDeliveredOn() != null) {
            throw new IllegalStateException("Doręczenie już zarejestrowane; użyj korekty z uzasadnieniem");
        }
        LocalDate effective = method == DeliveryMethod.AWIZO ? date.plusDays(AWIZO_DAYS) : date;
        d.delivered(effective, method);
        audit.record(userId, "TK_REFERENT", "rejestracja doręczenia: " + method, d.getDocument().getTitle(), d.getDocument().getCaseFile().getDocket().format(), null);
        return startDeadline(d, effective);
    }

    /** Korekta daty doręczenia: termin zostaje przeliczony, timer przestawiony, stary termin uchylony (nie usunięty). */
    @TransactionAttribute(TransactionAttributeType.REQUIRED)
    public Optional<Deadline> correctReceipt(long deliveryId, LocalDate newDate, DeliveryMethod method, String justification, String userId) {
        if (justification == null || justification.isBlank()) {
            throw new IllegalArgumentException("Korekta daty doręczenia wymaga uzasadnienia");
        }
        Delivery d = em.find(Delivery.class, deliveryId);
        LocalDate effective = method == DeliveryMethod.AWIZO ? newDate.plusDays(AWIZO_DAYS) : newDate;
        d.delivered(effective, method);
        for (Deadline old : em.createQuery("select x from Deadline x where x.delivery = :d and x.status = :s", Deadline.class)
                .setParameter("d", d).setParameter("s", DeadlineStatus.BIEGNIE).getResultList()) {
            old.revoke();
        }
        audit.record(userId, "TK_REFERENT", "korekta daty doręczenia: " + justification, d.getDocument().getTitle(), d.getDocument().getCaseFile().getDocket().format(), null);
        return startDeadline(d, effective);
    }

    private Optional<Deadline> startDeadline(Delivery d, LocalDate deliveredOn) {
        Optional<ProceduralAct> act = actFor(d.getDocument());
        if (act.isEmpty()) {
            return Optional.empty();
        }
        DeadlineType type = deadlineTypeFor(act.get());
        if (type == null) {
            return Optional.empty();
        }
        DeadlineComputation c = holidays.calculator().sevenDaysFromDelivery(deliveredOn);
        CaseFile cf = d.getDocument().getCaseFile();
        Deadline deadline = new Deadline(cf, d, type, c.start(), c.end());
        em.persist(deadline);
        String variable = type == DeadlineType.BRAKI ? ProcessVariables.TERMIN_BRAKOW : ProcessVariables.TERMIN_ZAZALENIA;
        process.setDeadline(cf.getDocket(), variable, c.end());
        return Optional.of(deadline);
    }

    static DeadlineType deadlineTypeFor(ProceduralAct act) {
        String sub = act.getSubtype() == null ? "" : act.getSubtype();
        if (sub.startsWith("BRAKI")) {
            return DeadlineType.BRAKI;
        }
        if (sub.startsWith("ODMOWA")) {
            return DeadlineType.ZAZALENIE;
        }
        return null;
    }

    Optional<ProceduralAct> actFor(CaseDocument doc) {
        return em.createQuery("select a from ProceduralAct a where a.document = :d", ProceduralAct.class).setParameter("d", doc).getResultStream().findFirst();
    }

    public List<Delivery> unconfirmed() {
        return em.createQuery("select d from Delivery d where d.sentOn is not null and d.deliveredOn is null order by d.sentOn", Delivery.class).getResultList();
    }

    public List<Delivery> toSend() {
        return em.createQuery("select d from Delivery d where d.sentOn is null order by d.createdAt", Delivery.class).getResultList();
    }

    public List<Delivery> delivered() {
        return em.createQuery("select d from Delivery d where d.deliveredOn is not null order by d.deliveredOn desc", Delivery.class).getResultList();
    }

    public List<Deadline> runningDeadlines(CaseFile cf) {
        return em.createQuery("select x from Deadline x where x.caseFile = :c and x.status = :s", Deadline.class)
                .setParameter("c", cf).setParameter("s", DeadlineStatus.BIEGNIE).getResultList();
    }
}
