package services.k_int.interaction.alma.types;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.serde.annotation.Serdeable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.ToString;

import java.util.List;

@Data
@AllArgsConstructor
@Builder
@ToString(onlyExplicitlyIncluded = true)
@Serdeable
public class UserIdentifiers {
	@ToString.Include
	@JsonProperty("user_identifier")
	private List<UserIdentifier> identifiers;
}
