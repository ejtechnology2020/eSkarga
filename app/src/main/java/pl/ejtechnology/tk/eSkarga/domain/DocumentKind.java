package pl.ejtechnology.tk.eSkarga.domain;

/** Rodzaj dokumentu w spisie akt (brief §4.2). Rozróżniany ikoną i tekstem. */
public enum DocumentKind {
    PISMO_PROCESOWE("file-text"),
    ZALACZNIK("paperclip"),
    CZYNNOSC("pen"),
    DORECZENIE("mail"),
    NIEJAWNY("lock"),
    NOTATKA("note");

    private final String icon;

    DocumentKind(String icon) {
        this.icon = icon;
    }

    public String icon() {
        return icon;
    }
}
