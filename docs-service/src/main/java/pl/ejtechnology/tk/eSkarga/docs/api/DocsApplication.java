package pl.ejtechnology.tk.eSkarga.docs.api;

import jakarta.ws.rs.ApplicationPath;
import jakarta.ws.rs.core.Application;

/** Usługa dokumentów (D13, T-031): {@code /eskarga-docs/api/...}, wołana przez aplikację i workery z tokenem ID-users. */
@ApplicationPath("/api")
public class DocsApplication extends Application {
}
