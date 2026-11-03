package pl.ejtechnology.tk.eSkarga.search;

import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.hc.core5.http.HttpHost;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch._types.Refresh;
import org.opensearch.client.opensearch.core.SearchResponse;
import org.opensearch.client.transport.httpclient5.ApacheHttpClient5TransportBuilder;

/**
 * OpenSearch z analizatorem polskim (wtyczka {@code analysis-stempel}; ADR 0018). Indeks {@code eskarga-akta}:
 * pola tekstowe analizowane, sygnatura jako keyword; wyniki filtruje aplikacja ({@link SearchService}).
 */
public final class OpenSearchIndex implements SearchIndex {

    public static final String INDEX = "eskarga-akta";

    private final OpenSearchClient client;
    private volatile boolean ready;

    public OpenSearchIndex(String url) {
        this(new OpenSearchClient(ApacheHttpClient5TransportBuilder.builder(host(url)).build()));
    }

    private static HttpHost host(String url) {
        try {
            return HttpHost.create(url);
        } catch (java.net.URISyntaxException e) {
            throw new IllegalArgumentException("Niepoprawny adres OpenSearch: " + url, e);
        }
    }

    public OpenSearchIndex(OpenSearchClient client) {
        this.client = client;
    }

    private void ensureIndex() throws IOException {
        if (ready) {
            return;
        }
        boolean exists = client.indices().exists(e -> e.index(INDEX)).value();
        if (!exists) {
            client.indices().create(c -> c.index(INDEX)
                    .settings(s -> s.analysis(a -> a.analyzer("pl", an -> an.custom(cu -> cu.tokenizer("standard").filter("lowercase", "polish_stem")))))
                    .mappings(m -> m
                            .properties("typ", p -> p.keyword(k -> k))
                            .properties("sygnatura", p -> p.keyword(k -> k))
                            .properties("caseId", p -> p.long_(l -> l))
                            .properties("documentId", p -> p.long_(l -> l))
                            .properties("tytul", p -> p.text(t -> t.analyzer("pl")))
                            .properties("tekst", p -> p.text(t -> t.analyzer("pl")))
                            .properties("strony", p -> p.text(t -> t.analyzer("pl")))
                            .properties("karty", p -> p.keyword(k -> k))
                            .properties("data", p -> p.date(d -> d))));
        }
        ready = true;
    }

    @Override
    public void index(Doc doc) {
        try {
            ensureIndex();
            Map<String, Object> source = new LinkedHashMap<>();
            source.put("typ", doc.type());
            source.put("sygnatura", doc.docket());
            source.put("caseId", doc.caseId());
            source.put("documentId", doc.documentId());
            source.put("tytul", doc.title());
            source.put("tekst", doc.text() == null ? "" : doc.text());
            source.put("strony", doc.parties() == null ? "" : String.join(" ", doc.parties()));
            source.put("karty", doc.pages() == null ? "" : doc.pages());
            source.put("data", doc.date() == null ? null : doc.date().toString());
            client.index(i -> i.index(INDEX).id(doc.id()).document(source).refresh(Refresh.WaitFor));
        } catch (IOException e) {
            throw new IllegalStateException("OpenSearch: indeksowanie nieudane", e);
        }
    }

    @Override
    public void delete(String id) {
        try {
            client.delete(d -> d.index(INDEX).id(id));
        } catch (IOException e) {
            throw new IllegalStateException("OpenSearch: usunięcie nieudane", e);
        }
    }

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public List<SearchIndex.Hit> search(String query, int limit) {
        try {
            ensureIndex();
            SearchResponse<Map> resp = client.search(s -> s.index(INDEX).size(limit)
                    .query(q -> q.multiMatch(mm -> mm.query(query).fields("sygnatura^5", "tytul^3", "strony^2", "tekst")))
                    .highlight(h -> h.fields("tekst", f -> f.fragmentSize(160).numberOfFragments(1))), Map.class);
            List<SearchIndex.Hit> out = new ArrayList<>();
            for (org.opensearch.client.opensearch.core.search.Hit<Map> h : resp.hits().hits()) {
                Map<String, Object> src = h.source();
                if (src == null) {
                    continue;
                }
                String fragment = h.highlight() != null && h.highlight().get("tekst") != null && !h.highlight().get("tekst").isEmpty() ? h.highlight().get("tekst").get(0) : "";
                Object docId = src.get("documentId");
                Object data = src.get("data");
                out.add(new SearchIndex.Hit(h.id(), String.valueOf(src.get("typ")), String.valueOf(src.get("sygnatura")), ((Number) src.get("caseId")).longValue(),
                        docId == null ? null : ((Number) docId).longValue(), String.valueOf(src.get("tytul")), String.valueOf(src.get("karty")),
                        data == null ? null : LocalDate.parse(data.toString()), fragment, h.score() == null ? 0 : h.score()));
            }
            return out;
        } catch (IOException e) {
            throw new IllegalStateException("OpenSearch: wyszukiwanie nieudane", e);
        }
    }

    @Override
    public void clear() {
        try {
            if (client.indices().exists(e -> e.index(INDEX)).value()) {
                client.indices().delete(d -> d.index(INDEX));
            }
            ready = false;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public boolean available() {
        try {
            return client.ping().value();
        } catch (IOException e) {
            return false;
        }
    }
}
