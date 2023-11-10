package services.k_int.interaction.sierra.patrons;

import java.util.List;

import io.micronaut.serde.annotation.Serdeable;
import lombok.Builder;
import lombok.Data;

@Data
@Serdeable
@Builder
public class PatronPatch {
	List<String> emails;
	List<String> names;
	String pin;
	Integer patronType;
	Codes patronCodes;
	List<String> uniqueIds;
	String homeLibraryCode;
	List<String> barcodes;
	String expirationDate;
}
