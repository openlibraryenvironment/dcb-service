package services.k_int.interaction.sierra.patrons;

import io.micronaut.serde.annotation.Serdeable;
import lombok.Builder;
import lombok.Data;

@Data
@Serdeable
@Builder
public class InternalPatronValidation {
	String authMethod;
	String patronId;
	String patronSecret;
}
