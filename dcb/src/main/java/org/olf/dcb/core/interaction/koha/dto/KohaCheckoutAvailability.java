package org.olf.dcb.core.interaction.koha.dto;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonProperty;

import io.micronaut.serde.annotation.Serdeable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * GET /api/v1/checkouts/availability: what Koha would block, ask staff to confirm, or
 * warn about before lending an item to a patron. DCB reads it only to say why a checkout
 * was refused, so the confirmation token Koha also issues is not kept.
 */
@Serdeable
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KohaCheckoutAvailability {
	@JsonProperty("blockers")
	private Map<String, Object> blockers;

	@JsonProperty("confirms")
	private Map<String, Object> confirms;

	@JsonProperty("warnings")
	private Map<String, Object> warnings;
}
