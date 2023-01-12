package org.olf.reshare.dcb.ingest.model;

import java.util.List;
import java.util.function.Consumer;

import javax.validation.constraints.NotEmpty;

import org.immutables.value.Value.Immutable;

import services.k_int.interaction.DefaultImmutableStyle;

@Immutable
@DefaultImmutableStyle
public interface Author {
	
	@NotEmpty
	String name();

	List<Identifier> identifiers();

	public static class Builder extends AuthorImpl.Builder {
		public Builder addIdentifiers( Consumer<Identifier.Builder> consumer ) {
			addIdentifiers( Identifier.build(consumer) );
			return this;
		}
	}

	public static Author build(Consumer<Builder> consumer) {
		Builder builder = builder();
		consumer.accept(builder);
		return builder.build();
	}

	public static Builder builder() {
		return new Builder();
	}
}