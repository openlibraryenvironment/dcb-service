package services.k_int.interaction.alma.types;

import io.micronaut.serde.annotation.Serdeable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import services.k_int.interaction.alma.AlmaLocation;

import java.util.List;

@Data
@AllArgsConstructor
@Builder
@Serdeable
public class AlmaGroupedLocationResponse {
	List<AlmaLocation> locations;
}

