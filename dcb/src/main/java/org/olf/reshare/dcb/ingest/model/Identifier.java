package org.olf.reshare.dcb.ingest.model;

import java.util.function.Consumer;

import javax.validation.constraints.NotNull;

import org.immutables.value.Value.Immutable;

import services.k_int.interaction.DefaultImmutableStyle;

@Immutable
@DefaultImmutableStyle
public interface Identifier {
	@NotNull
	String namespace();

	@NotNull
	String value();

	public static class Builder extends IdentifierImpl.Builder {
	}

	public static Identifier build(Consumer<Builder> consumer) {
		Builder builder = builder();
		consumer.accept(builder);
		return builder.build();
	}

	public static Builder builder() {
		return new Builder();
	}
}
