package org.olf.reshare.dcb.ingest;

import java.util.UUID;
import java.util.function.Consumer;

import javax.validation.constraints.NotEmpty;

import org.immutables.value.Value.Immutable;

import io.micronaut.core.annotation.NonNull;
import services.k_int.interaction.DefaultImmutableStyle;

@Immutable
@DefaultImmutableStyle
public interface IngestRecord {
	@NonNull
	@NotEmpty
	UUID uuid();

	@NonNull
	@NotEmpty
	String title();

	public static class Builder extends IngestRecordImpl.Builder {
	}

	public static IngestRecord build(Consumer<Builder> consumer) {
		Builder builder = builder();
		consumer.accept(builder);
		return builder.build();
	}

	public static Builder builder() {
		return new Builder();
	}
}
