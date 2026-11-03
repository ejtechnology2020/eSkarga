package pl.ejtechnology.tk.eSkarga.docs.templates;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.Writer;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import freemarker.core.HTMLOutputFormat;
import freemarker.core.TemplateClassResolver;
import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateException;
import freemarker.template.TemplateExceptionHandler;

/**
 * FreeMarker 2.3.x w trybie piaskownicy (CLAUDE.md zasada 13, D13):
 * <ul>
 *   <li>{@code new_builtin_class_resolver = ALLOWS_NOTHING_RESOLVER} – brak {@code ?new};</li>
 *   <li>{@code api_builtin_enabled = false} – brak {@code ?api} i wywołań metod Javy;</li>
 *   <li>automatyczne escapowanie HTML (format wyjścia HTML);</li>
 *   <li>limit czasu renderowania i rozmiaru wyniku.</li>
 * </ul>
 * Szablony edytowane z interfejsu (S-24) są danymi niezaufanymi, dlatego żadne ustawienie nie jest luzowane.
 */
public final class FreemarkerTemplateRenderer implements TemplateRenderer {

    public static final long DEFAULT_TIMEOUT_MS = 5_000;
    public static final int DEFAULT_MAX_OUTPUT_CHARS = 2_000_000;

    private final Configuration configuration;
    private final long timeoutMs;
    private final int maxOutputChars;
    private final ExecutorService executor = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "freemarker-render");
        t.setDaemon(true);
        return t;
    });

    public FreemarkerTemplateRenderer() {
        this(DEFAULT_TIMEOUT_MS, DEFAULT_MAX_OUTPUT_CHARS);
    }

    public FreemarkerTemplateRenderer(long timeoutMs, int maxOutputChars) {
        this.timeoutMs = timeoutMs;
        this.maxOutputChars = maxOutputChars;
        Configuration cfg = new Configuration(Configuration.VERSION_2_3_34);
        cfg.setDefaultEncoding("UTF-8");
        cfg.setLocale(java.util.Locale.of("pl", "PL"));
        cfg.setNewBuiltinClassResolver(TemplateClassResolver.ALLOWS_NOTHING_RESOLVER);
        cfg.setAPIBuiltinEnabled(false);
        cfg.setOutputFormat(HTMLOutputFormat.INSTANCE);
        cfg.setAutoEscapingPolicy(Configuration.ENABLE_IF_DEFAULT_AUTO_ESCAPING_POLICY);
        cfg.setTemplateExceptionHandler(TemplateExceptionHandler.RETHROW_HANDLER);
        cfg.setLogTemplateExceptions(false);
        cfg.setWrapUncheckedExceptions(true);
        cfg.setAttemptExceptionReporter((te, env) -> { });
        this.configuration = cfg;
    }

    @Override
    public String render(String templateName, String source, Map<String, Object> model) {
        Future<String> future = executor.submit(() -> doRender(templateName, source, model));
        try {
            return future.get(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            throw new TemplateRenderingException("Przekroczono limit czasu renderowania szablonu " + templateName, e);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof TemplateRenderingException tre) {
                throw tre;
            }
            throw new TemplateRenderingException("Błąd szablonu " + templateName + ": " + cause.getMessage(), cause);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new TemplateRenderingException("Przerwano renderowanie szablonu " + templateName, e);
        }
    }

    private String doRender(String templateName, String source, Map<String, Object> model) throws IOException, TemplateException {
        Template template = new Template(templateName, new StringReader(source), configuration);
        LimitedWriter out = new LimitedWriter(maxOutputChars);
        template.process(model, out);
        return out.toString();
    }

    /** Writer z limitem rozmiaru wyniku. */
    static final class LimitedWriter extends Writer {
        private final StringWriter delegate = new StringWriter();
        private final int limit;
        private int written;

        LimitedWriter(int limit) {
            this.limit = limit;
        }

        @Override
        public void write(char[] cbuf, int off, int len) {
            written += len;
            if (written > limit) {
                throw new TemplateRenderingException("Wynik szablonu przekracza limit " + limit + " znaków", null);
            }
            delegate.write(cbuf, off, len);
        }

        @Override
        public void flush() {
        }

        @Override
        public void close() {
        }

        @Override
        public String toString() {
            return delegate.toString();
        }
    }
}
