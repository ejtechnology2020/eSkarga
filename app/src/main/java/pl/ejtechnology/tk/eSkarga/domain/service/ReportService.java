package pl.ejtechnology.tk.eSkarga.domain.service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import jakarta.ejb.Stateless;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import pl.ejtechnology.tk.eSkarga.domain.CaseStatus;
import pl.ejtechnology.tk.eSkarga.security.RequiresRole;
import pl.ejtechnology.tk.eSkarga.domain.model.CaseFile;
import pl.ejtechnology.tk.eSkarga.domain.model.CaseFileVersion;
import pl.ejtechnology.tk.eSkarga.domain.model.ClosingMode;

/**
 * Raporty i statystyki (T-050, UC-19, FR-32, §8.3): wpływ i załatwienia w miesiącach, sposoby zakończenia, odsetek nadania biegu
 * i uwzględnionych zażaleń, czas wstępnej kontroli (mediana, p90), zaległości, obciążenie. Dane do rocznej Informacji TK.
 */
@Stateless
@RequiresRole   // domyślnie: dowolna rola eSkargi (odczyty); zmiany stanu – reguły na metodach
public class ReportService {

    /** Zestawienie za okres. */
    public record Summary(long intake, long disposed, Map<ClosingMode, Long> byMode, double admissionRatio, long medianDays, long p90Days,
                          Map<Integer, Long> intakeByMonth, Map<Integer, Long> disposedByMonth) {
    }

    @PersistenceContext(unitName = "repertorium")
    EntityManager em;

    public Summary summary(String symbol, LocalDate from, LocalDate to) {
        List<CaseFile> entered = em.createQuery("select c from CaseFile c where c.docketSymbol = :s and c.enteredOn between :a and :b", CaseFile.class)
                .setParameter("s", symbol).setParameter("a", from).setParameter("b", to).getResultList();
        List<CaseFileVersion> closed = em.createQuery("select v from CaseFileVersion v where v.caseFile.docketSymbol = :s and v.closingMode is not null "
                        + "and v.closedOn between :a and :b and v.version = (select max(x.version) from CaseFileVersion x where x.caseFile = v.caseFile)", CaseFileVersion.class)
                .setParameter("s", symbol).setParameter("a", from).setParameter("b", to).getResultList();
        Map<ClosingMode, Long> byMode = new LinkedHashMap<>();
        for (ClosingMode m : ClosingMode.values()) {
            byMode.put(m, 0L);
        }
        List<Long> durations = new ArrayList<>();
        Map<Integer, Long> intakeByMonth = new LinkedHashMap<>();
        Map<Integer, Long> disposedByMonth = new LinkedHashMap<>();
        for (int m = from.getMonthValue(); m <= to.getMonthValue() && from.getYear() == to.getYear(); m++) {
            intakeByMonth.put(m, 0L);
            disposedByMonth.put(m, 0L);
        }
        for (CaseFile c : entered) {
            intakeByMonth.merge(c.getEnteredOn().getMonthValue(), 1L, Long::sum);
        }
        for (CaseFileVersion v : closed) {
            byMode.merge(v.getClosingMode(), 1L, Long::sum);
            disposedByMonth.merge(v.getClosedOn().getMonthValue(), 1L, Long::sum);
            durations.add(ChronoUnit.DAYS.between(v.getCaseFile().getEnteredOn(), v.getClosedOn()));
        }
        long admitted = byMode.getOrDefault(ClosingMode.NADANIE_BIEGU, 0L);
        double ratio = closed.isEmpty() ? 0 : admitted * 100.0 / closed.size();
        return new Summary(entered.size(), closed.size(), byMode, ratio, percentile(durations, 50), percentile(durations, 90), intakeByMonth, disposedByMonth);
    }

    /** Sprawy bez czynności (zmiany wersji) od co najmniej {@code days} dni – zaległości (§8.3). */
    public List<CaseFile> backlog(int days, LocalDate today) {
        return em.createQuery("select v.caseFile from CaseFileVersion v where v.closingMode is null and v.version = "
                        + "(select max(x.version) from CaseFileVersion x where x.caseFile = v.caseFile) and v.createdAt < :limit order by v.createdAt", CaseFile.class)
                .setParameter("limit", today.minusDays(days).atStartOfDay(pl.ejtechnology.tk.eSkarga.web.AppClock.WARSAW).toInstant()).getResultList();
    }

    /** Odsetek uwzględnionych zażaleń. */
    public double appealsUpheldRatio() {
        Long all = em.createQuery("select count(a) from Appeal a where a.outcome is not null", Long.class).getSingleResult();
        Long upheld = em.createQuery("select count(a) from Appeal a where a.outcome = 'UWZGLEDNIONE'", Long.class).getSingleResult();
        return all == 0 ? 0 : upheld * 100.0 / all;
    }

    public long countByStatus(CaseStatus status) {
        return em.createQuery("select count(v) from CaseFileVersion v where v.status = :s and v.version = (select max(x.version) from CaseFileVersion x where x.caseFile = v.caseFile)", Long.class)
                .setParameter("s", status).getSingleResult();
    }

    static long percentile(List<Long> values, int p) {
        if (values.isEmpty()) {
            return 0;
        }
        List<Long> sorted = new ArrayList<>(values);
        Collections.sort(sorted);
        int idx = (int) Math.ceil(p / 100.0 * sorted.size()) - 1;
        return sorted.get(Math.max(0, Math.min(idx, sorted.size() - 1)));
    }
}
