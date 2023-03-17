package org.olf.reshare.dcb.item.availability;

import io.micronaut.serde.annotation.Serdeable;
import lombok.Data;

@Data
@Serdeable
public class Status {
	private final String code;
	private final String displayText;
}
