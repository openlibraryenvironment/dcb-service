package services.k_int.interaction.alma.types.error;

import io.micronaut.serde.annotation.Serdeable;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Serdeable
@NoArgsConstructor
@Getter
@Setter
public class AlmaErrorList {
	private List<AlmaError> error;
}
