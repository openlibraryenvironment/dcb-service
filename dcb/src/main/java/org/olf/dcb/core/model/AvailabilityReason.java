package org.olf.dcb.core.model;

import io.micronaut.serde.annotation.Serdeable;

@Serdeable
public record AvailabilityReason(String code, String label) {
}
