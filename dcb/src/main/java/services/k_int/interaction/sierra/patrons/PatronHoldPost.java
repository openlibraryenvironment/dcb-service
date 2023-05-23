package services.k_int.interaction.sierra.patrons;

import io.micronaut.serde.annotation.Serdeable;
import lombok.Data;

@Data
@Serdeable
public class PatronHoldPost {
	private String recordType;
	private Integer recordNumber;
	private String pickupLocation;
	private String neededBy;
	private Integer numberOfCopies;
	private String note;
}
