package org.olf.reshare.dcb.ingest.model;

import java.util.function.Consumer;

import javax.validation.constraints.NotNull;

import io.micronaut.serde.annotation.Serdeable;
import lombok.Builder;
import lombok.Data;
import lombok.Value;

@Serdeable
@Builder
@Value
@Data
public class Identifier {
	@NotNull
	String namespace;

	@NotNull
	String value;
	
	public static class IdentifierBuilder {}
	
	public static Identifier build(Consumer<IdentifierBuilder> consumer) {
		IdentifierBuilder builder = builder();
		consumer.accept(builder);
		return builder.build();
	}
}
