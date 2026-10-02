package org.olf.dcb.core.interaction.koha.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import io.micronaut.serde.annotation.Serdeable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// https://api.koha-community.org/#tag/patron_categories/operation/listPatronCategories
@Serdeable
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KohaPatronCategory {
	@JsonProperty("patron_category_id")
	private String patronCategoryId;

	@JsonProperty("name")
	private String name;
}
