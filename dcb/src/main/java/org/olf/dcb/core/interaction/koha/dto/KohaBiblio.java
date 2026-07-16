package org.olf.dcb.core.interaction.koha.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.serde.annotation.Serdeable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Serdeable
public class KohaBiblio {

	@JsonProperty("biblio_id")
	private Integer biblioId;

	@JsonProperty("framework_id")
	private String frameworkId;

	@JsonProperty("title")
	private String title;

	@JsonProperty("author")
	private String author;

	@JsonProperty("medium")
	private String medium;

	@JsonProperty("subtitle")
	private String subtitle;

	@JsonProperty("part_number")
	private String partNumber;

	@JsonProperty("part_name")
	private String partName;

	@JsonProperty("copyright_date")
	private Integer copyrightDate;

	@JsonProperty("date_created")
	private String dateCreated;

	@JsonProperty("timestamp")
	private String timestamp;

	@JsonProperty("notes")
	private String notes;

	@JsonProperty("serial")
	private Boolean serial;

	@JsonProperty("abstract")
	private String biblioAbstract;
}
