package pl.ejtechnology.tk.eSkarga.web;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.RequestScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import jakarta.inject.Named;

import pl.ejtechnology.tk.eSkarga.security.OidcSettings;
import pl.ejtechnology.tk.eSkarga.web.demo.DemoDataset;

/** Wybór źródła danych widoków: {@code eskarga.security.mode=demo} → dane przykładowe, inaczej usługi domenowe (ADR 0017). */
@ApplicationScoped
public class ViewDataProducer {

    @Inject
    OidcSettings settings;
    @Inject
    DemoDataset demo;
    @Inject
    Instance<JpaViewData> jpa;

    @Produces
    @RequestScoped
    @Named("dane")
    public ViewData viewData() {
        return settings.isDemo() ? demo : jpa.get();
    }
}
