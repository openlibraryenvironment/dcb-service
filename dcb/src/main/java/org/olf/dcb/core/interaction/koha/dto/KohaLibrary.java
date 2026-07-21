package org.olf.dcb.core.interaction.koha.dto;

import io.micronaut.serde.annotation.Serdeable;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Serdeable
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KohaLibrary {

	@JsonProperty("library_id")
	private String libraryId;

	@JsonProperty("name")
	private String name;

	@JsonProperty("address1")
	private String address1;

	@JsonProperty("address2")
	private String address2;

	@JsonProperty("address3")
	private String address3;

	@JsonProperty("city")
	private String city;

	@JsonProperty("state")
	private String state;

	@JsonProperty("postal_code")
	private String postalCode;

	@JsonProperty("country")
	private String country;

	@JsonProperty("phone")
	private String phone;

	@JsonProperty("fax")
	private String fax;

	@JsonProperty("email")
	private String email;

	@JsonProperty("ill_email")
	private String illEmail;

	@JsonProperty("reply_to_email")
	private String replyToEmail;

	@JsonProperty("return_path_email")
	private String returnPathEmail;

	@JsonProperty("url")
	private String url;

	@JsonProperty("ip")
	private String ip;

	@JsonProperty("notes")
	private String notes;

	@JsonProperty("geolocation")
	private String geolocation;

	@JsonProperty("marc_org_code")
	private String marcOrgCode;

	@JsonProperty("pickup_location")
	private Boolean pickupLocation;

	@JsonProperty("public")
	private Boolean isPublic;

	@JsonProperty("needs_override")
	private Boolean needsOverride;
}
