package pl.ejtechnology.tk.eSkarga.workers.transfer;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.Set;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.operaton.bpm.client.task.ExternalTask;
import org.operaton.bpm.client.task.ExternalTaskService;

import pl.ejtechnology.tk.eSkarga.workers.JsonHttp;
import pl.ejtechnology.tk.eSkarga.workers.Topics;
import pl.ejtechnology.tk.eSkarga.workers.WorkerConfig;

/** UC-12, T-028: przekazanie z kluczem idempotencji – ponowienie po błędzie nie tworzy drugiej sprawy w SK. */
class MeritTransferHandlerTest {

    static WireMockServer wm;
    static WorkerConfig cfg;

    @BeforeAll
    static void start() {
        wm = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        wm.start();
        String base = "http://localhost:" + wm.port();
        cfg = new WorkerConfig("http://silnik", "w1", 1, 1000, Set.of(Topics.PRZEKAZANIE_MERYTORYCZNE), "", "", "", "", "", base + "/app", base + "/docs", base + "/ext", 30);
    }

    @AfterAll
    static void stop() {
        wm.stop();
    }

    @Test
    void ponowienieZTymSamymKluczemNieTworzyDuplikatu() {
        wm.stubFor(get(urlEqualTo("/app/sprawy/Ts+312%2F26/przekazanie")).willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json")
                .withBody("{\"sygnatura\":\"Ts 312/26\",\"rodzaj\":\"TS\",\"kluczIdempotencji\":\"PRZEKAZ-Ts-312-26\",\"strony\":[]}")));
        // SK: pierwsza próba – awaria po stronie SK (500), druga – sukces; ten sam Idempotency-Key
        wm.stubFor(post(urlPathEqualTo("/ext/repertorium-sk/sprawy")).inScenario("sk").whenScenarioStateIs(Scenario.STARTED)
                .withHeader("Idempotency-Key", equalTo("PRZEKAZ-Ts-312-26"))
                .willReturn(aResponse().withStatus(500)).willSetStateTo("drugi"));
        wm.stubFor(post(urlPathEqualTo("/ext/repertorium-sk/sprawy")).inScenario("sk").whenScenarioStateIs("drugi")
                .withHeader("Idempotency-Key", equalTo("PRZEKAZ-Ts-312-26"))
                .willReturn(aResponse().withStatus(201).withHeader("Content-Type", "application/json").withBody("{\"sygnatura\":\"SK 9/26\"}")));

        MeritTransferHandler handler = new MeritTransferHandler(cfg, new JsonHttp(null));
        ExternalTask task = mock(ExternalTask.class);
        when(task.getId()).thenReturn("et-1");
        when(task.getBusinessKey()).thenReturn("Ts 312/26");
        when(task.getRetries()).thenReturn(null).thenReturn(4);
        ExternalTaskService service = mock(ExternalTaskService.class);

        handler.execute(task, service);
        verify(service).handleFailure(eq(task), anyString(), anyString(), eq(5), anyLong());
        verify(service, never()).complete(any(), any());

        handler.execute(task, service);
        verify(service).complete(eq(task), eq(Map.of(MeritTransferHandler.VAR_RELATED_DOCKET, "SK 9/26")));
        wm.verify(2, postRequestedFor(urlPathEqualTo("/ext/repertorium-sk/sprawy")).withHeader("Idempotency-Key", equalTo("PRZEKAZ-Ts-312-26")));
        verify(service, times(1)).handleFailure(any(ExternalTask.class), anyString(), anyString(), anyInt(), anyLong());
    }
}
