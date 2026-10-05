package services.k_int.interaction.sierra.patrons;

import io.micronaut.serde.annotation.Serdeable;
import lombok.Builder;
import lombok.Data;
import lombok.ToString;

@Data
@Serdeable
@Builder
public class PatronValidation {
	@ToString.Exclude
	String barcode;
	@ToString.Exclude
	String pin;
	Boolean caseSensitivity;
}
