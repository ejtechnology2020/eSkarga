package pl.ejtechnology.tk.eSkarga.docs.api;

import java.io.ByteArrayInputStream;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import jakarta.inject.Inject;
import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonString;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import pl.ejtechnology.tk.eSkarga.docs.hsm.AuditChainSigner;
import pl.ejtechnology.tk.eSkarga.docs.signature.TwoStepPadesSigner;

/** Podpis dwuetapowy PAdES (D10, T-040) i podpis łańcucha audytu kluczem HSM (T-037). */
@Path("/podpis")
public class SignatureResource {

    @Inject
    DocsServices services;

    /** Krok 1: {@code {"pdf": b64, "certyfikaty": [b64 DER…], "algorytm": "RSA_SHA256", "powod": "...", "miejsce": "Warszawa"}} → sesja i skrót. */
    @POST
    @Path("/dane-do-podpisu")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public JsonObject dataToSign(JsonObject body) {
        byte[] pdf = Base64.getDecoder().decode(body.getString("pdf"));
        List<X509Certificate> chain = new ArrayList<>();
        try {
            CertificateFactory cf = CertificateFactory.getInstance("X.509");
            for (JsonString s : body.getJsonArray("certyfikaty").getValuesAs(JsonString.class)) {
                chain.add((X509Certificate) cf.generateCertificate(new ByteArrayInputStream(Base64.getDecoder().decode(s.getString()))));
            }
        } catch (java.security.cert.CertificateException e) {
            throw new IllegalArgumentException("Niepoprawny certyfikat podpisującego");
        }
        TwoStepPadesSigner.DataToSign d = services.signer().dataToSign(pdf, chain, Instant.now(), body.getString("algorytm", "RSA_SHA256"),
                body.getString("powod", "Podpis sędziego Trybunału Konstytucyjnego"), body.getString("miejsce", "Warszawa"));
        return Json.createObjectBuilder().add("sesja", d.sessionId()).add("skrot", d.digestBase64()).add("algorytmSkrotu", d.digestAlgorithm())
                .add("algorytmPodpisu", d.signatureAlgorithm()).build();
    }

    /** Krok 2: {@code {"sesja": "...", "podpis": b64}} → podpisany PDF (PAdES-B-T, gdy skonfigurowano TSA). */
    @POST
    @Path("/zloz")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces("application/pdf")
    public Response sign(JsonObject body) {
        byte[] signed = services.signer().signDocument(body.getString("sesja"), Base64.getDecoder().decode(body.getString("podpis")));
        return Response.ok(signed).header("Cache-Control", "no-store").build();
    }

    /** Podpis ostatniego skrótu łańcucha audytu kluczem z HSM: {@code {"skrot": "hex"}}. */
    @POST
    @Path("/audyt")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public JsonObject signAuditChain(JsonObject body) {
        AuditChainSigner.Signed s = services.auditSigner().sign(body.getString("skrot"));
        return Json.createObjectBuilder().add("podpis", s.signatureBase64()).add("certyfikat", s.certificateBase64()).add("algorytm", AuditChainSigner.ALGORITHM).build();
    }
}
