package services.k_int.interaction.alma.types.error;

import io.micronaut.serde.annotation.Serdeable;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Serdeable
@NoArgsConstructor
@Getter
@Setter
public class AlmaErrorResponse {
	private boolean errorsExist;
	private AlmaErrorList errorList;

	@Override
	public String toString() {
		return "AlmaErrorResponse{" +
			"errorsExist=" + errorsExist +
			", errorList=" + errorList +
			'}';
	}
}
