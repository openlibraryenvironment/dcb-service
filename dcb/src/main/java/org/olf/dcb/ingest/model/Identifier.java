package org.olf.dcb.ingest.model;

import java.util.function.Consumer;

import jakarta.validation.constraints.NotNull;

import io.micronaut.serde.annotation.Serdeable;
import lombok.Builder;
import lombok.Data;
import lombok.Value;

@Data
@Value
@Builder
@Serdeable
public class Identifier {
	
	@NotNull
	String namespace;

	@NotNull
	String value;

	Integer confidence;
	
	public static class IdentifierBuilder {}
	
	public static Identifier build(Consumer<IdentifierBuilder> consumer) {
		IdentifierBuilder builder = builder();
		consumer.accept(builder);
		return builder.build();
	}
}
