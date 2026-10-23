package org.olf.dcb.core.interaction.koha.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import io.micronaut.serde.annotation.Serdeable;

/** Koha's answer to POST /api/v1/biblios, which is the new biblionumber and not a biblio. */
@Serdeable
public record KohaCreatedBiblio(@JsonProperty("id") Long id) {
}
