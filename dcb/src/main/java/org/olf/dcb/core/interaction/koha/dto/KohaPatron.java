package org.olf.dcb.core.interaction.koha.dto;

import io.micronaut.serde.annotation.Serdeable;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.util.List;

@Serdeable
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KohaPatron {

	@JsonProperty("patron_id")
	private Long patronId;

	@JsonProperty("cardnumber")
	private String cardnumber;

	@JsonProperty("surname")
	private String surname;

	@JsonProperty("firstname")
	private String firstname;

	@JsonProperty("email")
	private String email;

	@JsonProperty("library_id")
	private String libraryId;

	@JsonProperty("category_id")
	private String categoryId;

	@JsonProperty("userid")
	private String userid;

	@JsonProperty("date_enrolled")
	private String dateEnrolled;

	@JsonProperty("expiry_date")
	private String expiryDate;

	@JsonProperty("date_of_birth")
	private String dateOfBirth;

	@JsonProperty("city")
	private String city;

	@JsonProperty("country")
	private String country;

	@JsonProperty("phone")
	private String phone;

	@JsonProperty("mobile")
	private String mobile;

	@JsonProperty("privacy")
	private Integer privacy;

	@JsonProperty("extended_attributes")
	private List<KohaPatronAttribute> extendedAttributes;

	@Override
	public String toString() {
		// Do not include names in logs for privacy reasons
		return "KohaPatron{" +
			"patronId=" + patronId +
			", cardnumber='" + cardnumber + '\'' +
			", libraryId='" + libraryId + '\'' +
			", categoryId='" + categoryId + '\'' +
			'}';
	}
}
