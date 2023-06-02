package org.olf.reshare.dcb.ingest.model;

import io.micronaut.serde.annotation.Serdeable;
import lombok.Builder;
import lombok.Data;
import lombok.Singular;
import lombok.Value;
import org.olf.reshare.dcb.ingest.model.Identifier.IdentifierBuilder;

import javax.validation.constraints.NotEmpty;
import java.util.List;
import java.util.function.Consumer;

@Serdeable
@Data
@Builder
@Value
public class Author {

	@NotEmpty
	String name;

	@Singular
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
