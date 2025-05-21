package services.k_int.interaction.alma.types.items;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.serde.annotation.Serdeable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import services.k_int.interaction.alma.types.CodeValuePair;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Serdeable
public class AlmaItemLoanResponse {

	@JsonProperty("loan_id")
	private String loanId;

	@JsonProperty("circ_desk")
	private CodeValuePair circDesk;

	@JsonProperty("return_circ_desk")
	private CodeValuePair returnCircDesk;

	@JsonProperty("library")
	private CodeValuePair library;

	@JsonProperty("user_id")
	private String userId;

	@JsonProperty("item_barcode")
	private String itemBarcode;

	@JsonProperty("due_date")
	private String dueDate;

	@JsonProperty("loan_status")
	private String loanStatus;

	@JsonProperty("loan_date")
	private String loanDate;

	@JsonProperty("return_date")
	private String returnDate;

	@JsonProperty("returned_by")
	private CodeValuePair returnedBy;

	@JsonProperty("process_status")
	private String processStatus;

	@JsonProperty("mms_id")
	private String mmsId;

	@JsonProperty("holding_id")
	private String holdingId;

	@JsonProperty("item_id")
	private String itemId;

	@JsonProperty("title")
	private String title;

	@JsonProperty("author")
	private String author;

	@JsonProperty("description")
	private String description;

	@JsonProperty("publication_year")
	private String publicationYear;

	@JsonProperty("location_code")
	private CodeValuePair locationCode;

	@JsonProperty("item_policy")
	private CodeValuePair itemPolicy;

	@JsonProperty("call_number")
	private String callNumber;

	@JsonProperty("loan_fine")
	private Float loanFine;

	@JsonProperty("request_id")
	private CodeValuePair requestId;

	@JsonProperty("renewable")
	private Boolean renewable;

	@JsonProperty("last_renew_date")
	private String lastRenewDate;

	@JsonProperty("last_renew_status")
	private CodeValuePair lastRenewStatus;
}
