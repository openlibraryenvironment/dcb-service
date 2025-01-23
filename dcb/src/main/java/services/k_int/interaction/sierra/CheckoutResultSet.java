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
public class CheckoutResultSet {
//https://sandbox.iii.com/iii/sierra-api/swagger/index.html#!/patrons/Get_checkout_data_for_a_single_patron_record_get_20

	@JsonProperty("total")
	private int total;

	@JsonProperty("start")
	private int start;

	@JsonProperty("entries")
	private List<CheckoutEntry> entries;
}
