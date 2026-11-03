package pl.ejtechnology.tk.eSkarga.workers;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

import com.sun.net.httpserver.HttpServer;

/** Metryki Prometheus (T-042, T-054): liczniki zadań ukończonych i nieudanych per temat, {@code /metrics} i {@code /health}. */
public final class WorkerMetrics {

    private static final Map<String, LongAdder> COMPLETED = new ConcurrentHashMap<>();
    private static final Map<String, LongAdder> FAILED = new ConcurrentHashMap<>();
    private static HttpServer server;

    private WorkerMetrics() {
    }

    public static void completed(String topic) {
        COMPLETED.computeIfAbsent(topic, t -> new LongAdder()).increment();
    }

    public static void failed(String topic) {
        FAILED.computeIfAbsent(topic, t -> new LongAdder()).increment();
    }

    public static String render() {
        StringBuilder sb = new StringBuilder();
        sb.append("# TYPE eskarga_worker_tasks_completed_total counter\n");
        COMPLETED.forEach((t, v) -> sb.append("eskarga_worker_tasks_completed_total{topic=\"").append(t).append("\"} ").append(v.sum()).append('\n'));
        sb.append("# TYPE eskarga_worker_tasks_failed_total counter\n");
        FAILED.forEach((t, v) -> sb.append("eskarga_worker_tasks_failed_total{topic=\"").append(t).append("\"} ").append(v.sum()).append('\n'));
        sb.append("# TYPE jvm_memory_used_bytes gauge\njvm_memory_used_bytes ").append(Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()).append('\n');
        return sb.toString();
    }

    static void start(WorkerConfig cfg) {
        try {
            int port = Integer.parseInt(System.getenv().getOrDefault("ESKARGA_WORKER_METRICS_PORT", "9404"));
            server = HttpServer.create(new InetSocketAddress(port), 0);
            server.createContext("/metrics", ex -> write(ex, 200, render(), "text/plain; version=0.0.4"));
            server.createContext("/health", ex -> write(ex, 200, "{\"status\":\"UP\",\"worker\":\"" + cfg.workerId() + "\"}", "application/json"));
            server.start();
        } catch (IOException | NumberFormatException e) {
            // metryki są pomocnicze; brak portu nie zatrzymuje workera
        }
    }

    static void stop() {
        if (server != null) {
            server.stop(0);
        }
    }

    private static void write(com.sun.net.httpserver.HttpExchange ex, int status, String body, String type) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().add("Content-Type", type);
        ex.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(bytes);
        }
    }
}
