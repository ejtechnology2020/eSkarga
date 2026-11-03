package pl.ejtechnology.tk.eSkarga.web;

import java.util.EnumSet;
import java.util.Set;

import pl.ejtechnology.tk.eSkarga.security.AppRole;

import static pl.ejtechnology.tk.eSkarga.security.AppRole.TK_ADMIN;
import static pl.ejtechnology.tk.eSkarga.security.AppRole.TK_ARCHIWISTA;
import static pl.ejtechnology.tk.eSkarga.security.AppRole.TK_ASYSTENT;
import static pl.ejtechnology.tk.eSkarga.security.AppRole.TK_BIURO_PODAWCZE;
import static pl.ejtechnology.tk.eSkarga.security.AppRole.TK_KIEROWNIK;
import static pl.ejtechnology.tk.eSkarga.security.AppRole.TK_PREZES;
import static pl.ejtechnology.tk.eSkarga.security.AppRole.TK_REFERENT;
import static pl.ejtechnology.tk.eSkarga.security.AppRole.TK_SEDZIA;

/**
 * Inwentarz ekranów (brief §5) z grupą menu, adresem i rolami, które widzą pozycję.
 * Menu ma sześć stałych grup w tej samej kolejności dla wszystkich ról (brief §3).
 */
public enum Screen {
    S01(Group.MOJA_PRACA, "/pulpit.xhtml", all()),
    S02(Group.MOJA_PRACA, "/wyszukiwarka.xhtml", all()),
    S03(Group.WPLYW, "/wplyw/rejestracja.xhtml", of(TK_BIURO_PODAWCZE)),
    S04(Group.WPLYW, "/wplyw/dziennik.xhtml", of(TK_BIURO_PODAWCZE, TK_REFERENT, TK_KIEROWNIK)),
    S05(Group.WPLYW, "/wplyw/etykieta.xhtml", of(TK_BIURO_PODAWCZE)),
    S06(Group.SPRAWY, "/sprawy/kolejka.xhtml", of(TK_REFERENT)),
    S07(Group.SPRAWY, "/sprawy/zalozenie.xhtml", of(TK_REFERENT)),
    S08(Group.SPRAWY, "/sprawy/repertorium.xhtml", of(TK_REFERENT, TK_KIEROWNIK, TK_ASYSTENT, TK_SEDZIA, TK_PREZES)),
    S09(Group.SPRAWY, "/sprawy/korekta.xhtml", of(TK_REFERENT, TK_KIEROWNIK)),
    S14(Group.SPRAWY, "/akta/podglad.xhtml", of(TK_REFERENT, TK_ASYSTENT, TK_SEDZIA, TK_PREZES, TK_KIEROWNIK)),
    S15(Group.CZYNNOSCI, "/czynnosci/edytor.xhtml", of(TK_ASYSTENT, TK_SEDZIA, TK_PREZES)),
    S16(Group.CZYNNOSCI, "/czynnosci/podpis.xhtml", of(TK_SEDZIA, TK_PREZES)),
    S17(Group.CZYNNOSCI, "/czynnosci/wyznaczenie.xhtml", of(TK_PREZES)),
    S18(Group.CZYNNOSCI, "/czynnosci/sklad.xhtml", of(TK_PREZES)),
    S10(Group.REJESTRY, "/rejestry/ekspedycja.xhtml", of(TK_REFERENT)),
    S11(Group.REJESTRY, "/rejestry/zazalenia.xhtml", of(TK_REFERENT, TK_KIEROWNIK, TK_PREZES)),
    S12(Group.REJESTRY, "/rejestry/pisma-ogolne.xhtml", of(TK_REFERENT)),
    S13(Group.REJESTRY, "/rejestry/wglad.xhtml", of(TK_REFERENT)),
    S27(Group.REJESTRY, "/rejestry/publikacja.xhtml", of(TK_REFERENT)),
    S19(Group.NADZOR, "/nadzor/monitor.xhtml", of(TK_KIEROWNIK)),
    S20(Group.NADZOR, "/nadzor/raporty.xhtml", of(TK_KIEROWNIK, TK_PREZES)),
    S21(Group.NADZOR, "/nadzor/archiwum.xhtml", of(TK_ARCHIWISTA)),
    S22(Group.NADZOR, "/admin/uzytkownicy.xhtml", of(TK_ADMIN)),
    S23(Group.NADZOR, "/admin/upowaznienia.xhtml", of(TK_ADMIN, TK_PREZES)),
    S24(Group.NADZOR, "/admin/slowniki.xhtml", of(TK_ADMIN)),
    S25(Group.NADZOR, "/admin/audyt.xhtml", of(TK_ADMIN));

    /** Grupy menu w kolejności wyświetlania. Ikony: identyfikatory z {@link Icons}. */
    public enum Group {
        MOJA_PRACA("home"),
        WPLYW("inbox"),
        SPRAWY("folder"),
        CZYNNOSCI("pen"),
        REJESTRY("book"),
        NADZOR("chart");

        private final String icon;

        Group(String icon) {
            this.icon = icon;
        }

        public String icon() {
            return icon;
        }
    }

    private final Group group;
    private final String path;
    private final Set<AppRole> roles;

    Screen(Group group, String path, Set<AppRole> roles) {
        this.group = group;
        this.path = path;
        this.roles = roles;
    }

    public Group group() {
        return group;
    }

    public String path() {
        return path;
    }

    public Set<AppRole> roles() {
        return roles;
    }

    /** Czy rola widzi pozycję. Prezes (upoważniony) widzi też pozycje sędziego, bo nadal jest sędzią. */
    public boolean visibleFor(AppRole role) {
        return roles.contains(role) || (role == TK_PREZES && roles.contains(TK_SEDZIA));
    }

    /** Klucz komunikatu z nazwą ekranu. */
    public String messageKey() {
        return "screen." + name();
    }

    /** Czytelny identyfikator z briefu, np. „S-14”. */
    public String briefId() {
        return "S-" + name().substring(1);
    }

    public static Screen byPath(String viewId) {
        for (Screen s : values()) {
            if (s.path.equals(viewId)) {
                return s;
            }
        }
        return null;
    }

    private static Set<AppRole> all() {
        return EnumSet.allOf(AppRole.class);
    }

    private static Set<AppRole> of(AppRole first, AppRole... rest) {
        return EnumSet.of(first, rest);
    }
}
