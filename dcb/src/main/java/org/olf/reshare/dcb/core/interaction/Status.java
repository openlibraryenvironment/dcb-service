package org.olf.reshare.dcb.core.interaction;

import io.micronaut.serde.annotation.Serdeable;
import lombok.Data;

@Data
@Serdeable
public class Status {
	final String code;
	final String displayText;
	final String dueDate;
}
