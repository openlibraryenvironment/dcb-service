package services.k_int.interaction.sierra.patrons;

import io.micronaut.serde.annotation.Serdeable;
import lombok.Builder;
import lombok.Data;

@Data
@Serdeable
@Builder
public class PatronPatch {
	String[] emails;
	String[] names;
	String pin;
	Integer patronType;
	Codes patronCodes;
	String[] uniqueIds;
	String homeLibraryCode;
	String[] barcodes;
}
