package services.k_int.interaction.sierra.patrons;

import io.micronaut.serde.annotation.Serdeable;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@Serdeable
public class PatronHoldPost {
	private String recordType;
	private Integer recordNumber;
	private String pickupLocation;
	private String neededBy;
	private Integer numberOfCopies;
	private String note;
}
