package services.k_int.interaction.alma;

import com.fasterxml.jackson.annotation.JsonProperty;

import io.micronaut.serde.annotation.Serdeable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** One circulation desk, from GET /almaws/v1/conf/libraries/{library}/circ-desks/{desk}. */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Serdeable
public class AlmaCirculationDesk {
	String code;
	String name;
	@JsonProperty("has_hold_shelf")
	Boolean hasHoldShelf;
}
