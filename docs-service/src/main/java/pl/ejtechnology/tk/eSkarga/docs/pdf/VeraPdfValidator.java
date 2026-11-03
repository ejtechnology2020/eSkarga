package pl.ejtechnology.tk.eSkarga.docs.pdf;

import java.io.ByteArrayInputStream;
import java.util.List;

import org.verapdf.gf.foundry.VeraGreenfieldFoundryProvider;
import org.verapdf.pdfa.Foundries;
import org.verapdf.pdfa.PDFAParser;
import org.verapdf.pdfa.PDFAValidator;
import org.verapdf.pdfa.flavours.PDFAFlavour;
import org.verapdf.pdfa.results.TestAssertion;
import org.verapdf.pdfa.results.ValidationResult;

/** veraPDF (biblioteka greenfield) – walidacja PDF/A-2b każdego pisma i skanu (NFR-11, D13). */
public final class VeraPdfValidator implements PdfAValidator {

    static {
        VeraGreenfieldFoundryProvider.initialise();
    }

    @Override
    public Result validate(byte[] pdf) {
        try (PDFAParser parser = Foundries.defaultInstance().createParser(new ByteArrayInputStream(pdf), PDFAFlavour.PDFA_2_B);
             PDFAValidator validator = Foundries.defaultInstance().createValidator(PDFAFlavour.PDFA_2_B, false)) {
            ValidationResult result = validator.validate(parser);
            List<String> violations = result.getTestAssertions().stream()
                    .filter(a -> a.getStatus() == TestAssertion.Status.FAILED)
                    .map(a -> a.getRuleId().getSpecification().getId() + " " + a.getRuleId().getClause() + "-" + a.getRuleId().getTestNumber() + ": " + a.getMessage())
                    .distinct().toList();
            return new Result(result.isCompliant(), violations);
        } catch (Exception e) {
            return new Result(false, List.of("Nie udało się sparsować dokumentu jako PDF/A-2b: " + e.getMessage()));
        }
    }
}
