package services.k_int.interaction.sierra.patrons;

import io.micronaut.serde.annotation.Serdeable;
import lombok.Data;

@Data
@Serdeable
public class PatronPatch {
	String[] emails;
	String[] names;
	String pin;
	Integer patronType;
	Codes patronCodes;
	String[] uniqueIds;
	String homeLibraryCode;
}
