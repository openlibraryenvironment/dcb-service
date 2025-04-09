package org.olf.dcb.devtools.interaction.dummy.responder;

import io.micronaut.serde.annotation.Serdeable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.util.Map;

@Builder
@Data
@AllArgsConstructor
@Serdeable
public class DummyResponse {
	private String message;
	private int statusCode;
	private Map<String, Object> data;
}
