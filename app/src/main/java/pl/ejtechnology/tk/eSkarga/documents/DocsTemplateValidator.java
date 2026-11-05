package pl.ejtechnology.tk.eSkarga.documents;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import pl.ejtechnology.tk.eSkarga.domain.service.TemplateService;

/** Walidacja szablonu pisma w usłudze dokumentów (D13): bean CDI dla {@link TemplateService.TemplateValidator}; w testach – atrapa. */
@ApplicationScoped
public class DocsTemplateValidator implements TemplateService.TemplateValidator {

    @Inject
    DocsGateway docs;

    @Override
    public TemplateService.Validation validate(String code, String sourceXhtml) {
        DocsGateway.TemplateCheck check = docs.validateTemplate(code, sourceXhtml);
        return new TemplateService.Validation(check.ok(), check.errors());
    }
}
