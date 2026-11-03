package pl.ejtechnology.tk.eSkarga.docs.templates;

/** Błąd kompilacji albo renderowania szablonu (w tym próba wyjścia z piaskownicy). */
public class TemplateRenderingException extends RuntimeException {
    public TemplateRenderingException(String message, Throwable cause) {
        super(message, cause);
    }
}
