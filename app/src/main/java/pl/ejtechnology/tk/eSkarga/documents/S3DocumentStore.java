package pl.ejtechnology.tk.eSkarga.documents;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.ObjectLockMode;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.ServerSideEncryption;

import pl.ejtechnology.tk.eSkarga.security.OidcSettings;

/**
 * Implementacja na AWS SDK v2 (dev: SeaweedFS, prod: Ceph RGW). Klucze i hasła przychodzą z konfiguracji środowiska,
 * nigdy z repozytorium. Object Lock w trybie compliance dla dokumentów zamkniętych (D12).
 */
@ApplicationScoped
public class S3DocumentStore implements DocumentStore {

    private S3Client s3;
    private String bucket;

    @PostConstruct
    void init() {
        bucket = OidcSettings.value("eskarga.s3.bucket", "eskarga-akta");
        String endpoint = OidcSettings.value("eskarga.s3.endpoint", "http://localhost:8333");
        String access = OidcSettings.value("eskarga.s3.accessKey", "");
        String secret = OidcSettings.value("eskarga.s3.secretKey", "");
        s3 = S3Client.builder()
                .endpointOverride(URI.create(endpoint))
                .region(Region.of(OidcSettings.value("eskarga.s3.region", "eu-central-1")))
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(access, secret)))
                .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
                .build();
    }

    @Override
    public Stored put(String key, InputStream content, long size, String contentType, boolean lockCompliance) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            DigestInputStream din = new DigestInputStream(content, md);
            PutObjectRequest.Builder req = PutObjectRequest.builder().bucket(bucket).key(key).contentType(contentType)
                    .serverSideEncryption(ServerSideEncryption.AES256);
            if (lockCompliance) {
                req.objectLockMode(ObjectLockMode.COMPLIANCE);
            }
            s3.putObject(req.build(), RequestBody.fromInputStream(din, size));
            return new Stored(key, size, HexFormat.of().formatHex(md.digest()), contentType);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public InputStream get(String key, Optional<Range> range) {
        GetObjectRequest.Builder req = GetObjectRequest.builder().bucket(bucket).key(key);
        range.ifPresent(r -> req.range("bytes=" + r.start() + "-" + r.endInclusive()));
        return s3.getObject(req.build());
    }

    @Override
    public boolean verifySha256(String key, String expectedSha256) {
        try (InputStream in = get(key, Optional.empty())) {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] buf = new byte[65536];
            int n;
            while ((n = in.read(buf)) > 0) {
                md.update(buf, 0, n);
            }
            return HexFormat.of().formatHex(md.digest()).equalsIgnoreCase(expectedSha256);
        } catch (IOException | NoSuchAlgorithmException e) {
            return false;
        }
    }
}
