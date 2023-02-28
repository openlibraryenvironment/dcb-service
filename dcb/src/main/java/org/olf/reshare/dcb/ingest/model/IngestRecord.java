package org.olf.reshare.dcb.ingest.model;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

import javax.validation.constraints.NotEmpty;

import org.olf.reshare.dcb.ingest.model.Author.AuthorBuilder;
import org.olf.reshare.dcb.ingest.model.Identifier.IdentifierBuilder;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import lombok.Builder;
import lombok.Data;
import lombok.Singular;
import lombok.Value;

@Data
@Builder
@Value
public class IngestRecord {
	@NonNull
	@NotEmpty
	UUID uuid;

	@Nullable
	String title;

	@Singular
	Set<String> otherTitles;

	@Singular
	Set<Identifier> identifiers; // ICCN, ISBN, ISSN, controlNumber, controlNumberIdentifier

	@Nullable
	Author author;

	@Singular
	Set<Author> otherAuthors;

	@Nullable
	String materialType;

	@Nullable
	String bibLevel;

//  @Nullable String edition();
//  List<PublicationInformation> publicationInformation();
//  List<Description> descriptions();

	public static class IngestRecordBuilder {

		public IngestRecordBuilder addIdentifier(Consumer<IdentifierBuilder> consumer) {
			identifier(Identifier.build(consumer));
			return this;
		}
		
		public IngestRecordBuilder addIdentifiers(Identifier... ids) {
			identifiers(List.of(ids));
			return this;
		}

		public IngestRecordBuilder author(Consumer<AuthorBuilder> consumer) {
			author( Author.build(consumer) );
			return this;
		}

		public IngestRecordBuilder author(Author author) {
			this.author = author;
			return this;
		}

		public IngestRecordBuilder addOtherAuthor(Consumer<AuthorBuilder> consumer) {
			otherAuthor(Author.build(consumer));
			return this;
		}
	}

	public static IngestRecord build(Consumer<IngestRecordBuilder> consumer) {
		IngestRecordBuilder builder = builder();
		consumer.accept(builder);
		return builder.build();
	}
}
