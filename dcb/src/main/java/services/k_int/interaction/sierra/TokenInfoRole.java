package services.k_int.interaction.sierra;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.serde.annotation.Serdeable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import java.util.List;

@Data
@Builder
@AllArgsConstructor
@Serdeable
public class TokenInfoRole {
	@JsonProperty("name")
	private String name;
	@JsonProperty("tokenLifetime")
	private Integer tokenLifetime;
	@JsonProperty("permissions")
	private List<String> permissions;
}
