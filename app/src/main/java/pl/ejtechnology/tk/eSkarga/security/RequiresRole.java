package pl.ejtechnology.tk.eSkarga.security;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import jakarta.enterprise.util.Nonbinding;
import jakarta.interceptor.InterceptorBinding;

/**
 * Rola ogólna wymagana do wywołania usługi (obrona w głąb, poziom 1 z D4). Role bierze {@link RoleCheckInterceptor} z {@link CurrentUser}
 * (token ID-users w trybie OIDC, osoba fikcyjna w demo), nie z kontenera – tak samo jak reszta aplikacji. Adnotacja na klasie to
 * domyślna reguła dla wszystkich metod, adnotacja na metodzie ją zastępuje. Nie zastępuje {@link CaseAccessPolicy} (dostęp do
 * konkretnej sprawy, zespół sędziego, upoważnienie) – te sprawdzenia zostają w usługach.
 *
 * <ul>
 * <li>{@link #value()} pusta – wystarczy dowolna rola eSkargi (zalogowana osoba z rolą; odczyty);</li>
 * <li>{@link #workers()} – wywołanie dopuszczone także z API workerów (token client credentials z rolą {@code eskarga-worker});</li>
 * <li>{@link #system()} – wywołanie z infrastruktury bez użytkownika (nasłuch procesu, start aplikacji): kontrola ról wyłączona,
 * metoda sama odpowiada za ograniczenie skutków.</li>
 * </ul>
 */
@InterceptorBinding
@Inherited
@Documented
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequiresRole {

    @Nonbinding
    AppRole[] value() default {};

    @Nonbinding
    boolean workers() default false;

    @Nonbinding
    boolean system() default false;
}
