package pl.ejtechnology.tk.eSkarga.web.faces;

import jakarta.faces.application.Resource;
import jakarta.faces.application.ResourceHandler;
import jakarta.faces.application.ResourceHandlerWrapper;
import jakarta.faces.application.ResourceWrapper;

import pl.ejtechnology.tk.eSkarga.web.AppInfo;

/**
 * Adresy własnych zasobów aplikacji (biblioteki {@code js}, {@code css}, komponenty {@code tk}) dostają parametr {@code v=<wersja buildu>}.
 * Faces w trybie produkcyjnym każe przeglądarce trzymać zasoby 7 dni (max-age), więc po wdrożeniu nowego WAR-a przeglądarka
 * używała starego {@code pdf-viewer.js} / {@code eskarga.css}, a strony już nowych znaczników (06.11.2026: chipy dokumentów bez skryptu,
 * dopasowanie ponad 100 %). Inny adres po każdym buildzie = świeży plik bez wymuszania odświeżenia u użytkowników.
 * Zasoby PrimeFaces i webjarów mają własne wersje w adresie – bez zmian.
 */
public class VersionedResourceHandler extends ResourceHandlerWrapper {

    static final java.util.Set<String> OWN_LIBRARIES = java.util.Set.of("js", "css", "tk", "img", "fonts");

    public VersionedResourceHandler(ResourceHandler wrapped) {
        super(wrapped);
    }

    @Override
    public Resource createResource(String resourceName, String libraryName) {
        return version(super.createResource(resourceName, libraryName), libraryName);
    }

    @Override
    public Resource createResource(String resourceName, String libraryName, String contentType) {
        return version(super.createResource(resourceName, libraryName, contentType), libraryName);
    }

    static Resource version(Resource resource, String libraryName) {
        if (resource == null || libraryName == null || !OWN_LIBRARIES.contains(libraryName)) {
            return resource;
        }
        return new ResourceWrapper(resource) {
            @Override
            public String getRequestPath() {
                return withVersion(getWrapped().getRequestPath(), AppInfo.buildStamp());
            }
        };
    }

    /** Dokleja {@code v=stamp} do adresu zasobu (z zachowaniem istniejących parametrów, np. {@code ln=js}). */
    static String withVersion(String path, String stamp) {
        if (path == null || stamp == null || stamp.isBlank() || path.contains("v=" + stamp)) {
            return path;
        }
        return path + (path.contains("?") ? "&" : "?") + "v=" + stamp;
    }
}
