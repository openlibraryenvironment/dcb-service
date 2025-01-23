package services.k_int.interaction.sierra;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.serde.annotation.Serdeable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@AllArgsConstructor
@Serdeable
public class CheckoutEntry {
	@JsonProperty("id")
	private String id;
	@JsonProperty("patron")
	private String patron;
	@JsonProperty("item")
	private String item;
	@JsonProperty("barcode")
	private String barcode;
	@JsonProperty("dueDate")
	private String dueDate;
	@JsonProperty("callNumber")
	private String callNumber;
	@JsonProperty("numberOfRenewals")
	private Integer numberOfRenewals;
	@JsonProperty("outDate")
	private String outDate;
	@JsonProperty("recallDate")
	private String recallDate;
}
