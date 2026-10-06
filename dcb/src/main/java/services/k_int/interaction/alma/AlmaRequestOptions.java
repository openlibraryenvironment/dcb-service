package services.k_int.interaction.alma;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

import io.micronaut.serde.annotation.Serdeable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import services.k_int.interaction.alma.types.CodeValuePair;

/**
 * Shape taken from a live Alma sandbox response; Ex Libris documents only the object's name:
 * {"request_option":[{"type":{"value":"HOLD","desc":"Hold"},"request_url":"…"}]}
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Serdeable
public class AlmaRequestOptions {
	@JsonProperty("request_option")
	List<Option> requestOptions;

	@Data
	@Builder
	@AllArgsConstructor
	@NoArgsConstructor
	@Serdeable
	public static class Option {
		CodeValuePair type;

		@JsonProperty("request_url")
		String requestUrl;
	}
}
