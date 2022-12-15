package org.olf.reshare.dcb.ingest.model;

import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

import javax.validation.constraints.NotEmpty;

import org.immutables.value.Value.Immutable;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import services.k_int.interaction.DefaultImmutableStyle;

@Immutable
@DefaultImmutableStyle
public interface IngestRecord {
	@NonNull
	@NotEmpty
	UUID uuid();

	@Nullable
	String title();

	Set<String> otherTitles();

	Set<Identifier> identifiers(); // ICCN, ISBN, ISSN, controlNumber, controlNumberIdentifier

	@Nullable
	Author author();

	Set<Author> otherAuthors();
	
	@Nullable String materialType();
	
	@Nullable String bibLevel();

//  @Nullable String edition();
//  List<PublicationInformation> publicationInformation();
//  List<Description> descriptions();

	public static class Builder extends IngestRecordImpl.Builder {
		
		public Builder addIdentifiers( Consumer<Identifier.Builder> consumer ) {
			addIdentifiers( Identifier.build(consumer) );
			return this;
		}
		
		public Builder author( Consumer<Author.Builder> consumer ) {
			author( Author.build(consumer) );
			return this;
		}
		
		public Builder addOtherAuthors( Consumer<Author.Builder> consumer ) {
			addOtherAuthors( Author.build(consumer) );
			return this;
		}
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
