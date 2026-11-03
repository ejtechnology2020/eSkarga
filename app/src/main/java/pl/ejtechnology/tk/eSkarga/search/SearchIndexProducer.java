package pl.ejtechnology.tk.eSkarga.search;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;

import pl.ejtechnology.tk.eSkarga.common.security.Settings;

/** {@code eskarga.search.url} (np. {@code http://opensearch:9200}) → OpenSearch; brak → indeks w pamięci (dev/demo). */
@ApplicationScoped
public class SearchIndexProducer {

    private SearchIndex index;

    @Produces
    @ApplicationScoped
    public SearchIndex index() {
        if (index == null) {
            String url = Settings.value("eskarga.search.url", "");
            index = url.isBlank() ? new InMemorySearchIndex() : new OpenSearchIndex(url);
        }
        return index;
    }
}
