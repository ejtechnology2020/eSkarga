package pl.ejtechnology.tk.eSkarga.domain.model;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import jakarta.persistence.UniqueConstraint;

import pl.ejtechnology.tk.eSkarga.domain.DocketNumber;
import pl.ejtechnology.tk.eSkarga.domain.DocketSymbol;

/**
 * Sprawa – wpis repertorium Ts/Tw (FR-06, FR-07). Sygnatura jest nadawana raz i nigdy nie zmieniana (BR-01);
 * zmienne dane wpisu są w wersjach ({@link CaseFileVersion}) – korekta dodaje wersję, nie aktualizuje (NFR-01, UC-22).
 */
@Entity
@Table(name = "case_file", uniqueConstraints = @UniqueConstraint(name = "uq_case_file_docket", columnNames = {"docket_symbol", "docket_number", "docket_year"}))
public class CaseFile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "docket_symbol", nullable = false, length = 3)
    private String docketSymbol;

    @Column(name = "docket_number", nullable = false)
    private int docketNumber;

    @Column(name = "docket_year", nullable = false)
    private int docketYear;

    @Column(name = "entered_on", nullable = false)
    private LocalDate enteredOn;

    @Column(name = "process_instance_id", length = 64)
    private String processInstanceId;

    @Column(name = "created_by", nullable = false, length = 120)
    private String createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @OneToMany(mappedBy = "caseFile", cascade = CascadeType.PERSIST)
    private List<CaseFileVersion> versions = new ArrayList<>();

    protected CaseFile() {
    }

    public CaseFile(DocketNumber docket, LocalDate enteredOn, String createdBy) {
        this.docketSymbol = docket.symbol().code();
        this.docketNumber = docket.number();
        this.docketYear = docket.year();
        this.enteredOn = enteredOn;
        this.createdBy = createdBy;
    }

    @Transient
    public DocketNumber getDocket() {
        return new DocketNumber(DocketSymbol.fromCode(docketSymbol), docketNumber, docketYear);
    }

    /** Aktualna wersja wpisu = najwyższy numer wersji obowiązującej (korekty oczekujące i odrzucone nie liczą się). */
    @Transient
    public CaseFileVersion current() {
        return versions.stream().filter(CaseFileVersion::isEffective).max(Comparator.comparingInt(CaseFileVersion::getVersion))
                .orElseThrow(() -> new IllegalStateException("Wpis bez wersji"));
    }

    /** Kolejny numer wersji (także po odrzuconych korektach – numeracja bez luk w historii). */
    @Transient
    public int nextVersionNo() {
        return versions.stream().mapToInt(CaseFileVersion::getVersion).max().orElse(0) + 1;
    }

    public CaseFileVersion addVersion(CaseFileVersion v) {
        versions.add(v);
        return v;
    }

    public Long getId() { return id; }
    public LocalDate getEnteredOn() { return enteredOn; }
    public String getProcessInstanceId() { return processInstanceId; }
    public void setProcessInstanceId(String processInstanceId) { this.processInstanceId = processInstanceId; }
    public String getCreatedBy() { return createdBy; }
    public Instant getCreatedAt() { return createdAt; }
    public List<CaseFileVersion> getVersions() { return versions; }
}
