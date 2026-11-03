package pl.ejtechnology.tk.eSkarga.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.operaton.bpm.engine.runtime.Job;
import org.operaton.bpm.engine.task.Task;

import pl.ejtechnology.tk.eSkarga.domain.CaseStatus;
import pl.ejtechnology.tk.eSkarga.domain.DeadlineType;
import pl.ejtechnology.tk.eSkarga.domain.DeliveryMethod;
import pl.ejtechnology.tk.eSkarga.domain.DocumentKind;
import pl.ejtechnology.tk.eSkarga.domain.model.ActStatus;
import pl.ejtechnology.tk.eSkarga.domain.model.Appeal;
import pl.ejtechnology.tk.eSkarga.domain.model.AppealOutcome;
import pl.ejtechnology.tk.eSkarga.domain.model.CaseDocument;
import pl.ejtechnology.tk.eSkarga.domain.model.CaseFile;
import pl.ejtechnology.tk.eSkarga.domain.model.Deadline;
import pl.ejtechnology.tk.eSkarga.domain.model.DeadlineStatus;
import pl.ejtechnology.tk.eSkarga.domain.model.Delivery;
import pl.ejtechnology.tk.eSkarga.domain.model.DeliveryChannel;
import pl.ejtechnology.tk.eSkarga.domain.model.PartyRole;
import pl.ejtechnology.tk.eSkarga.domain.model.ProceduralAct;
import pl.ejtechnology.tk.eSkarga.domain.model.ProceduralActKind;
import pl.ejtechnology.tk.eSkarga.process.ProcessVariables;
import pl.ejtechnology.tk.eSkarga.security.AppRole;
import pl.ejtechnology.tk.eSkarga.security.CaseAccessPolicy;
import pl.ejtechnology.tk.eSkarga.support.ServiceTestSupport;

/** UC-06 – UC-11: doręczenia, terminy z timerem procesu (D6) i zażalenia (T-027, T-041). */
class DeliveryAndAppealServiceTest extends ServiceTestSupport {

    private static final String SEDZIA = "sedzia.fikcyjny";

    /** Sprawa we wstępnej kontroli z sędzią; proces czeka na T_Kontrola. */
    private CaseFile caseInReview() {
        long letterId = tx(s -> registerLetter(s)).getId();
        CaseFile cf = tx(s -> createCase(s, letterId));
        run(s -> {
            if (s.delegations.policyDelegations(PREZES).isEmpty()) {
                s.delegations.add(PREZES, CaseAccessPolicy.FUNCTION_PRESIDENT, LocalDate.of(2025, 1, 1), LocalDate.of(2031, 12, 31), "Postanowienie (fikcyjne)", "admin");
            }
            s.judges.assign(cf.getId(), SEDZIA, PREZES, Set.of(AppRole.TK_PREZES));
        });
        return cf;
    }

    private Task task(CaseFile cf) {
        return engine.getTaskService().createTaskQuery().processInstanceBusinessKey(cf.getDocket().format()).singleResult();
    }

    /** Sędzia wydaje czynność z dokumentem w aktach; proces przechodzi do bramki zdarzeniowej z timerem. */
    private CaseDocument issueAct(CaseFile cf, ProcessVariables.WynikKontroli result, String subtype, String variable, CaseStatus status) {
        engine.getTaskService().complete(task(cf).getId(), ProcessVariables.reviewResult(result));
        CaseDocument doc = tx(s -> {
            CaseFile loaded = s.cases.require(cf.getId());
            CaseDocument d = new CaseDocument(loaded, result == ProcessVariables.WynikKontroli.BRAKI ? "Zarządzenie o usunięciu braków" : "Postanowienie o odmowie nadania dalszego biegu",
                    DocumentKind.CZYNNOSC, TODAY, SEDZIA);
            int last = s.cases.lastPage(loaded);
            d.paginate(1, last + 1, last + 2);
            s.em.persist(d);
            ProceduralAct act = new ProceduralAct(loaded, result == ProcessVariables.WynikKontroli.BRAKI ? ProceduralActKind.ZARZADZENIE : ProceduralActKind.POSTANOWIENIE,
                    subtype, clock.instant(), SEDZIA, "art. 61", ActStatus.PODPISANA);
            act.setDocument(d);
            s.em.persist(act);
            s.cases.changeStatus(cf.getId(), status, SEDZIA);
            return d;
        });
        engine.getTaskService().complete(task(cf).getId(), ProcessVariables.deadline(variable, date(TODAY.plusDays(30))));
        // worker doręczeń kończy external task; w aplikacji ekspedycja rejestruje wysyłkę (dispatch)
        var locked = engine.getExternalTaskService().fetchAndLock(1, "test-worker").topic("doreczenie-pisma", 60_000).execute();
        engine.getExternalTaskService().complete(locked.get(0).getId(), "test-worker");
        return doc;
    }

    private Job timer(CaseFile cf) {
        String piId = engine.getRuntimeService().createProcessInstanceQuery().processInstanceBusinessKey(cf.getDocket().format()).singleResult().getId();
        return engine.getManagementService().createJobQuery().processInstanceId(piId).timers().singleResult();
    }

    @Test
    void doreczenieDoPelnomocnikaUruchamiaTerminIPrzestawiaTimer() {
        CaseFile cf = caseInReview();
        CaseDocument order = issueAct(cf, ProcessVariables.WynikKontroli.BRAKI, "BRAKI_FORMALNE", ProcessVariables.TERMIN_BRAKOW, CaseStatus.WEZWANIE_DO_USUNIECIA_BRAKOW);
        assertThat(timer(cf)).isNotNull();

        Delivery d = tx(s -> s.deliveries.dispatch(order.getId(), DeliveryChannel.POCZTA_POLECONY_ZPO, TODAY, "RR123456789PL", REFERENT));
        assertThat(d.getAddressee().getRole()).as("BR-06").isEqualTo(PartyRole.PELNOMOCNIK);
        tx(s -> assertThat(s.deliveries.unconfirmed()).extracting(Delivery::getId).contains(d.getId()));

        // doręczenie do rąk 06.11.2026 (piątek) → 7 dni: 13.11.2026 (piątek)
        Deadline deadline = tx(s -> s.deliveries.registerReceipt(d.getId(), LocalDate.of(2026, 11, 6), DeliveryMethod.DO_RAK, REFERENT)).orElseThrow();
        assertThat(deadline.getKind()).isEqualTo(DeadlineType.BRAKI);
        assertThat(deadline.getEndsOn()).isEqualTo(LocalDate.of(2026, 11, 13));
        assertThat(timer(cf).getDuedate()).isEqualTo(date(LocalDate.of(2026, 11, 14)));
        assertThatThrownBy(() -> tx(s -> s.deliveries.registerReceipt(d.getId(), TODAY, DeliveryMethod.DO_RAK, REFERENT))).isInstanceOf(IllegalStateException.class);

        // korekta: faktycznie awizo 05.11 → skutek doręczenia 19.11 → termin 26.11; stary termin uchylony, nie usunięty
        Deadline corrected = tx(s -> s.deliveries.correctReceipt(d.getId(), LocalDate.of(2026, 11, 5), DeliveryMethod.AWIZO, "błędna data na zwrotce", REFERENT)).orElseThrow();
        assertThat(corrected.getEndsOn()).isEqualTo(LocalDate.of(2026, 11, 26));
        assertThat(timer(cf).getDuedate()).isEqualTo(date(LocalDate.of(2026, 11, 27)));
        run(s -> {
            CaseFile loaded = s.cases.require(cf.getId());
            List<Deadline> all = s.em.createQuery("select x from Deadline x where x.caseFile = :c order by x.id", Deadline.class).setParameter("c", loaded).getResultList();
            assertThat(all).hasSize(2);
            assertThat(all.get(0).getStatus()).isEqualTo(DeadlineStatus.UCHYLONY);
            assertThat(s.deliveries.runningDeadlines(loaded)).singleElement().extracting(Deadline::getId).isEqualTo(corrected.getId());
            assertThat(s.em.find(Delivery.class, d.getId()).getDeliveredOn()).isEqualTo(LocalDate.of(2026, 11, 19));
        });
        assertThatThrownBy(() -> tx(s -> s.deliveries.correctReceipt(d.getId(), TODAY, DeliveryMethod.DO_RAK, "", REFERENT))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void koniecTerminuWDzienWolnyPrzesuwaSieNaDzienRoboczy() {
        CaseFile cf = caseInReview();
        CaseDocument order = issueAct(cf, ProcessVariables.WynikKontroli.BRAKI, "BRAKI_FORMALNE", ProcessVariables.TERMIN_BRAKOW, CaseStatus.WEZWANIE_DO_USUNIECIA_BRAKOW);
        Delivery d = tx(s -> s.deliveries.dispatch(order.getId(), DeliveryChannel.EDORECZENIA, TODAY, null, REFERENT));
        // doręczenie 04.11.2026 (środa) → 7 dni = 11.11.2026 (Święto Niepodległości) → 12.11.2026
        Deadline deadline = tx(s -> s.deliveries.registerReceipt(d.getId(), LocalDate.of(2026, 11, 4), DeliveryMethod.DO_RAK, REFERENT)).orElseThrow();
        assertThat(deadline.getEndsOn()).isEqualTo(LocalDate.of(2026, 11, 12));
    }

    @Test
    void zazaleniePoOdmowieZOcenaTerminowosciIWykluczeniemSedziego() {
        CaseFile cf = caseInReview();
        assertThatThrownBy(() -> tx(s -> s.appeals.register(cf.getId(), TODAY, REFERENT))).isInstanceOf(IllegalStateException.class);

        CaseDocument refusal = issueAct(cf, ProcessVariables.WynikKontroli.ODMOWA, "ODMOWA_OCZYWISTA_BEZZASADNOSC", ProcessVariables.TERMIN_ZAZALENIA, CaseStatus.ODMOWA_TERMIN_NA_ZAZALENIE);
        Delivery d = tx(s -> s.deliveries.dispatch(refusal.getId(), DeliveryChannel.POCZTA_POLECONY_ZPO, TODAY, "RR1", REFERENT));
        Deadline deadline = tx(s -> s.deliveries.registerReceipt(d.getId(), LocalDate.of(2026, 11, 6), DeliveryMethod.DO_RAK, REFERENT)).orElseThrow();
        assertThat(deadline.getKind()).isEqualTo(DeadlineType.ZAZALENIE);
        assertThat(deadline.getEndsOn()).isEqualTo(LocalDate.of(2026, 11, 13));

        Appeal appeal = tx(s -> s.appeals.register(cf.getId(), LocalDate.of(2026, 11, 13), REFERENT));
        assertThat(appeal.getInTime()).isTrue();
        tx(s -> assertThat(s.cases.require(cf.getId()).current().getStatus()).isEqualTo(CaseStatus.ZAZALENIE_W_ROZPOZNANIU));
        assertThat(task(cf).getTaskDefinitionKey()).isEqualTo("T_Skieruj");

        // BR-10: skierowanie tylko przez Prezesa / upoważnionego; BR-07: bez sędziego odmowy; skład 3-osobowy
        assertThatThrownBy(() -> run(s -> s.appeals.referToPanel(appeal.getId(), List.of("a", "b", "c"), TODAY.plusDays(7), REFERENT, Set.of(AppRole.TK_REFERENT))))
                .isInstanceOf(SecurityException.class);
        assertThatThrownBy(() -> run(s -> s.appeals.referToPanel(appeal.getId(), List.of(SEDZIA, "b", "c"), TODAY.plusDays(7), PREZES, Set.of(AppRole.TK_PREZES))))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("BR-07");
        assertThatThrownBy(() -> run(s -> s.appeals.referToPanel(appeal.getId(), List.of("a", "b"), TODAY.plusDays(7), PREZES, Set.of(AppRole.TK_PREZES))))
                .isInstanceOf(IllegalArgumentException.class);
        run(s -> s.appeals.referToPanel(appeal.getId(), List.of("s1", "s2", "s3"), TODAY.plusDays(7), PREZES, Set.of(AppRole.TK_PREZES)));
        tx(s -> assertThat(s.em.find(Appeal.class, appeal.getId()).getPanelUserIds()).isEqualTo("s1,s2,s3"));

        run(s -> s.appeals.decide(appeal.getId(), AppealOutcome.NIEUWZGLEDNIONE, "s1"));
        tx(s -> assertThat(s.cases.require(cf.getId()).current().getStatus()).isEqualTo(CaseStatus.ZAKONCZONA_ODMOWA_PRAWOMOCNA));

        // BR-07: od nieuwzględnienia nie ma kolejnego zażalenia (nawet gdyby status na to pozwalał)
        run(s -> s.cases.changeStatus(cf.getId(), CaseStatus.ODMOWA_TERMIN_NA_ZAZALENIE, "test"));
        assertThatThrownBy(() -> tx(s -> s.appeals.register(cf.getId(), TODAY, REFERENT))).isInstanceOf(IllegalStateException.class).hasMessageContaining("BR-07");
    }

    @Test
    void zazalenieSpoznioneOznaczaneJakoPoTerminie() {
        CaseFile cf = caseInReview();
        CaseDocument refusal = issueAct(cf, ProcessVariables.WynikKontroli.ODMOWA, "ODMOWA_BRAKI", ProcessVariables.TERMIN_ZAZALENIA, CaseStatus.ODMOWA_TERMIN_NA_ZAZALENIE);
        Delivery d = tx(s -> s.deliveries.dispatch(refusal.getId(), DeliveryChannel.EDORECZENIA, TODAY, null, REFERENT));
        tx(s -> s.deliveries.registerReceipt(d.getId(), LocalDate.of(2026, 11, 4), DeliveryMethod.DO_RAK, REFERENT));
        Appeal appeal = tx(s -> s.appeals.register(cf.getId(), LocalDate.of(2026, 11, 20), REFERENT));
        assertThat(appeal.getInTime()).isFalse();
        assertThat(task(cf).getTaskDefinitionKey()).as("ocena terminowości jest pomocnicza – skład decyduje").isEqualTo("T_BezRozp");
    }
}
