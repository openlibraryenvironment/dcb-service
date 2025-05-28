package services.k_int.interaction.alma.types;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.serde.annotation.Serdeable;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Data
@AllArgsConstructor
@Builder
@Serdeable
public class UserIdentifier {
	// BARCODE, INST_ID, ILLIAD_ID, GoogleScholarID, ORCID,...
	@JsonProperty("id_type")
	WithAttr id_type;
	String value;
	String note;
	String status;
}
