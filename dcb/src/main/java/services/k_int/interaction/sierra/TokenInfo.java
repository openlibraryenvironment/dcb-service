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
public class TokenInfo {

	@JsonProperty("patronId")
	private String patronId;
	@JsonProperty("keyId")
	private String keyId;
	@JsonProperty("grantType")
	private String grantType;
	@JsonProperty("authorizationScheme")
	private String authorizationScheme;
	@JsonProperty("expiresIn")
	private Integer expiresIn;
	@JsonProperty("roles")
	private List<TokenInfoRole> roles;
}
