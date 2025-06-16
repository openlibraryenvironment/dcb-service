package services.k_int.interaction.alma.types.items;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.serde.annotation.Serdeable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
@Builder
@Serdeable
public class AlmaItemLoans {
	@JsonProperty("total_record_count")
	Integer recordCount;
	@JsonProperty("item_loan")
	List<AlmaItemLoan> loans;
}
