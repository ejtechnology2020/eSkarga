package pl.ejtechnology.tk.eSkarga.support;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

/**
 * Wstrzykiwanie zależności do beanów {@code @Stateless} bez kontenera: pola przypisywane po typie
 * (tak jak zrobiłby to CDI). Pozwala testować serwisy na H2 i wbudowanym silniku Operaton (ADR 0011).
 */
public final class Wiring {

    private Wiring() {
    }

    /** Ustawia każde pole obiektu {@code target}, do którego pasuje typem któraś z zależności. */
    public static <T> T inject(T target, Object... dependencies) {
        for (Class<?> c = target.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
            for (Field f : c.getDeclaredFields()) {
                if (Modifier.isStatic(f.getModifiers()) || Modifier.isFinal(f.getModifiers())) {
                    continue;
                }
                for (Object dep : dependencies) {
                    if (dep != null && f.getType().isInstance(dep)) {
                        try {
                            f.setAccessible(true);
                            if (f.get(target) == null) {
                                f.set(target, dep);
                            }
                        } catch (IllegalAccessException e) {
                            throw new IllegalStateException(e);
                        }
                        break;
                    }
                }
            }
        }
        return target;
    }
}
