package services.k_int.interaction.alma.types.error;

import io.micronaut.serde.annotation.Serdeable;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Serdeable
@Getter
@Setter
@NoArgsConstructor
public class AlmaError {
	private String errorCode;
	private String errorMessage;
	private String trackingId;

	@Override
	public String toString() {
		return "AlmaError{" +
			"errorCode='" + errorCode + '\'' +
			", errorMessage='" + errorMessage + '\'' +
			", trackingId='" + trackingId + '\'' +
			'}';
	}
}
