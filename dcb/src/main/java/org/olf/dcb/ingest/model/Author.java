package org.olf.dcb.ingest.model;

import java.util.List;
import java.util.function.Consumer;

import javax.validation.constraints.NotEmpty;

import org.olf.dcb.ingest.model.Identifier.IdentifierBuilder;

import io.micronaut.core.annotation.Creator;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.Singular;

@Serdeable
@Data
@Builder
@AllArgsConstructor(onConstructor = @__(@Creator))
public class Author {
	
	@NotEmpty
	String name;

	@Singular
	@Nullable
	List<Identifier> identifiers;

	public static class AuthorBuilder {
		public AuthorBuilder addIdentifier(Consumer<IdentifierBuilder> consumer) {
			identifier(Identifier.build(consumer));
			return this;
		}
	}

	public static Author build(Consumer<AuthorBuilder> consumer) {
		AuthorBuilder builder = builder();
		consumer.accept(builder);
		return builder.build();
	}
}
