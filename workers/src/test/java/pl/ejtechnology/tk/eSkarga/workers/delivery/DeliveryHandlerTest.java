package pl.ejtechnology.tk.eSkarga.workers.delivery;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.Set;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.operaton.bpm.client.task.ExternalTask;
import org.operaton.bpm.client.task.ExternalTaskService;

import pl.ejtechnology.tk.eSkarga.workers.JsonHttp;
import pl.ejtechnology.tk.eSkarga.workers.Topics;
import pl.ejtechnology.tk.eSkarga.workers.WorkerConfig;

/** T-043, FR-25/FR-26: wysyłka przez e-Doręczenia i pocztę na zaślepkach, raport nadania do aplikacji, mapowanie statusów. */
class DeliveryHandlerTest {

    static WireMockServer wm;
    static WorkerConfig cfg;

    @BeforeAll
    static void start() {
        wm = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        wm.start();
        String base = "http://localhost:" + wm.port();
        cfg = new WorkerConfig("http://silnik", "w1", 1, 1000, Set.of(Topics.DORECZENIE_PISMA), "", "", "", "", "", base + "/app", base + "/docs", base + "/ext", 30);
    }

    @AfterAll
    static void stop() {
        wm.stop();
    }

    @Test
    void wysylaEDoreczeniaIPoczteIRaportujeNadanie() {
        wm.stubFor(get(urlEqualTo("/app/doreczenia/do-wysylki?sygnatura=Ts+312%2F26")).willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json")
                .withBody("{\"doreczenia\":[" +
                        "{\"id\":7,\"kluczIdempotencji\":\"DOR-a\",\"kanal\":\"EDORECZENIA\",\"sygnatura\":\"Ts 312/26\",\"tytul\":\"Zarządzenie o brakach\",\"adresat\":\"adw. Anna Testowa\",\"adresEDoreczen\":\"AE:PL-1\"}," +
                        "{\"id\":8,\"kluczIdempotencji\":\"DOR-b\",\"kanal\":\"POCZTA_POLECONY_ZPO\",\"sygnatura\":\"Ts 312/26\",\"tytul\":\"Zarządzenie o brakach\",\"adresat\":\"Jan Przykładowy\",\"adres\":\"ul. Przykładowa 1\"}]}")));
        wm.stubFor(get(urlPathEqualTo("/app/doreczenia/7/dokument")).willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/pdf").withBody("%PDF-1.7 a")));
        wm.stubFor(get(urlPathEqualTo("/app/doreczenia/8/dokument")).willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/pdf").withBody("%PDF-1.7 b")));
        wm.stubFor(post(urlPathEqualTo("/ext/edoreczenia/przesylki")).withHeader("Idempotency-Key", equalTo("DOR-a"))
                .willReturn(aResponse().withStatus(201).withHeader("Content-Type", "application/json").withBody("{\"identyfikator\":\"EDO-123\"}")));
        wm.stubFor(post(urlPathEqualTo("/ext/poczta/przesylki")).withHeader("Idempotency-Key", equalTo("DOR-b"))
                .willReturn(aResponse().withStatus(201).withHeader("Content-Type", "application/json").withBody("{\"numerNadania\":\"RR123456789PL\"}")));
        wm.stubFor(post(urlPathEqualTo("/app/doreczenia/7/status")).willReturn(aResponse().withStatus(204)));
        wm.stubFor(post(urlPathEqualTo("/app/doreczenia/8/status")).willReturn(aResponse().withStatus(204)));

        ExternalTask task = mock(ExternalTask.class);
        when(task.getId()).thenReturn("et-2");
        when(task.getBusinessKey()).thenReturn("Ts 312/26");
        ExternalTaskService service = mock(ExternalTaskService.class);
        new DeliveryHandler(cfg, new JsonHttp(null)).execute(task, service);

        verify(service).complete(eq(task), eq(Map.of()));
        wm.verify(1, postRequestedFor(urlPathEqualTo("/app/doreczenia/7/status")).withRequestBody(matchingJsonPath("$.status", equalTo("WYSLANE")))
                .withRequestBody(matchingJsonPath("$.numerNadania", equalTo("EDO-123"))));
        wm.verify(1, postRequestedFor(urlPathEqualTo("/app/doreczenia/8/status")).withRequestBody(matchingJsonPath("$.numerNadania", equalTo("RR123456789PL"))));
        wm.verify(1, postRequestedFor(urlPathEqualTo("/ext/edoreczenia/przesylki")).withRequestBody(matchingJsonPath("$.dokument")));
    }

    @Test
    void brakPismDoWysylkiToPonowienieNieUkonczenie() {
        wm.stubFor(get(urlEqualTo("/app/doreczenia/do-wysylki?sygnatura=Tw+7%2F26")).willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json").withBody("{\"doreczenia\":[]}")));
        ExternalTask task = mock(ExternalTask.class);
        when(task.getId()).thenReturn("et-3");
        when(task.getBusinessKey()).thenReturn("Tw 7/26");
        when(task.getRetries()).thenReturn(3);
        ExternalTaskService service = mock(ExternalTaskService.class);
        new DeliveryHandler(cfg, new JsonHttp(null)).execute(task, service);
        verify(service).handleFailure(eq(task), anyString(), anyString(), eq(2), anyLong());
    }

    @Test
    void mapowanieStatusowOperatora() {
        assertThat(DeliveryHandler.mapOperatorStatus("DELIVERED")).isEqualTo("DORECZONE");
        assertThat(DeliveryHandler.mapOperatorStatus("awizowana")).isEqualTo("AWIZO");
        assertThat(DeliveryHandler.mapOperatorStatus("RETURNED")).isEqualTo("ZWROT");
        assertThat(DeliveryHandler.mapOperatorStatus("ACCEPTED")).isEqualTo("WYSLANE");
        assertThat(DeliveryHandler.mapOperatorStatus(null)).isEqualTo("NIEZNANY");
    }
}
