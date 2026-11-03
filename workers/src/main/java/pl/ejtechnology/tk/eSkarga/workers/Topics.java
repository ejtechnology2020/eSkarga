package pl.ejtechnology.tk.eSkarga.workers;

/** Tematy external tasks z modelu BPMN ({@code wstepna-kontrola.bpmn}) i zadania cykliczne workerów (§8 architektury). */
public final class Topics {

    /** Doręczenie pisma sędziego (zarządzenie o brakach, postanowienie o odmowie) – T_DoreczBraki / T_DoreczOdmowa. */
    public static final String DORECZENIE_PISMA = "doreczenie-pisma";
    public static final String PRZEKAZANIE_MERYTORYCZNE = "przekazanie-merytoryczne";
    /** Zadanie cykliczne (nie external task): OCR, PDF/A i walidacja podpisów nowych dokumentów przez usługę dokumentów. */
    public static final String DOKUMENT_PRZETWORZ = "dokument-przetworz";

    private Topics() {
    }
}
