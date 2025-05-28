package services.k_int.interaction.alma;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.serde.annotation.Serdeable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Serdeable
public class AlmaCircDesk {
	@JsonProperty("check_in")
	boolean checkIn;

	@JsonProperty("check_out")
	boolean checkOut;

	@JsonProperty("circ_desk_code")
	String circDeskCode;

	@JsonProperty("circ_desk_name")
	String circDeskName;

	String link;
	boolean reshelve;
}
