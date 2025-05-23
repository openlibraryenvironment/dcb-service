package services.k_int.interaction.alma.types.error;

import io.micronaut.serde.annotation.Serdeable;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Serdeable
@Getter
@Setter
public class AlmaErrorList {
	private List<AlmaError> error;

	public String toString() {
		return "AlmaErrorList{" +
			"error=" + error +
			'}';
	}
}
