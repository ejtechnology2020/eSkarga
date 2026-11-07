package pl.ejtechnology.tk.eSkarga.docs.convert;

/** Konwersja dokumentu biurowego (DOC, DOCX, ODT, RTF) do PDF – za interfejsem, bo konwerter to osobny składnik (ADR 0026). */
public interface DocumentConverter {

    /** @param fileName nazwa z rozszerzeniem (konwerter rozpoznaje po niej format); wynik: PDF (w miarę możliwości PDF/A-2b). */
    byte[] toPdf(byte[] content, String fileName);

    boolean available();

    /** Bez konfiguracji: każde wywołanie kończy się czytelnym błędem konfiguracyjnym (409 w API). */
    static DocumentConverter none() {
        return new DocumentConverter() {
            @Override
            public byte[] toPdf(byte[] content, String fileName) {
                throw new IllegalStateException("Konwerter dokumentów DOC/DOCX nie jest skonfigurowany (eskarga.docs.converterUrl / ESKARGA_DOCS_CONVERTERURL, ADR 0026)");
            }

            @Override
            public boolean available() {
                return false;
            }
        };
    }
}
