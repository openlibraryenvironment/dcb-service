package services.k_int.interaction.alma.types.error;

import io.micronaut.serde.annotation.Serdeable;

import java.util.List;

@Serdeable.Deserializable
public class AlmaErrorList {
	private List<AlmaError> error;

	public List<AlmaError> getError() { return error; }
	public void setError(List<AlmaError> error) { this.error = error; }
}
