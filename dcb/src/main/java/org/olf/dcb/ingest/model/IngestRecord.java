package org.olf.dcb.ingest.model;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import org.olf.dcb.core.model.HostLms;
import org.olf.dcb.core.model.clustering.CoreBibliographicMetadata;
import org.olf.dcb.ingest.model.Author.AuthorBuilder;
import org.olf.dcb.ingest.model.Identifier.IdentifierBuilder;

import io.micronaut.core.annotation.Introspected;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import lombok.Builder;
import lombok.Data;
import lombok.Singular;
import lombok.With;
import lombok.experimental.Accessors;

@Introspected
@Data
@Builder( toBuilder = true )
@Accessors(chain = true)
public class IngestRecord implements CoreBibliographicMetadata {
	@NonNull
	@NotEmpty
	UUID uuid;

	@NonNull
	@NotEmpty
	HostLms sourceSystem;

	@NonNull
	@NotEmpty
	String sourceRecordId;

	@Singular
	Set<String> otherTitles;

	@Singular
	Set<Identifier> identifiers; // ICCN, ISBN, ISSN, controlNumber, controlNumberIdentifier

	@Singular
	Set<Author> otherAuthors;

	@Nullable
	String materialType;

	@Nullable
	String bibLevel;
	
	@NonNull
	@NotNull
	@With
	UUID clusterRecordId;

	@Nullable
	String typeOfRecord;

	@Nullable
	Boolean suppressFromDiscovery;

	@Nullable
	Boolean deleted;

	// Create an internal version of the object for easy access to common bib properties
	@NonNull
	Map<String, Object> canonicalMetadata;
	
	@Nullable
  Integer metadataScore;

	// Some bib records can give us an indication of where the items are held (E.g. Sierra fixed field 026 and Variable field LOCATIONS)
	// This field lets us communicate those values downstream
	@Singular
	Set<String> heldAtLocations;

	@Nullable
  UUID sourceRecordUuid;

//  @Nullable String edition();
//  List<PublicationInformation> publicationInformation();
//  List<Description> descriptions();
//

	public String getIdentifier(String namespace) {
    if (identifiers == null) {
        return null;
    }
    return identifiers.stream()
            .filter(id -> namespace.equals(id.getNamespace()))
            .map(Identifier::getValue)
            .findFirst()
            .orElse(null);
	}  

	public static class IngestRecordBuilder {
		
		IngestRecordBuilder() {
			this.canonicalMetadata( new HashMap<>() );
		}
		
		private IngestRecordBuilder addToMd( String key, Object val ) {
			canonicalMetadata.put(key, val);
			return this;
		}
		
		public IngestRecordBuilder edition(String edition) {
			addToMd(CoreBibliographicMetadata.MD_EDITION, edition);
			return this;
		}

		public IngestRecordBuilder dateOfPublication(String dateOfPublication) {
			addToMd(CoreBibliographicMetadata.MD_DATE_OF_PUB, dateOfPublication);
			return this;
		}

		public IngestRecordBuilder largePrint(boolean largePrint) {
			addToMd(CoreBibliographicMetadata.MD_LARGE_PRINT, largePrint);
			return this;
		}

		public IngestRecordBuilder publisher(String publisher) {
			addToMd(CoreBibliographicMetadata.MD_PUBLISHER, publisher);
			return this;
		}

		public IngestRecordBuilder placeOfPublication(String placeOfPublication) {
			addToMd(CoreBibliographicMetadata.MD_PLACE_OF_PUB, placeOfPublication);
			return this;
		}

		public IngestRecordBuilder title(String title) {
			addToMd(CoreBibliographicMetadata.MD_TITLE, title);
			return this;
		}

		public IngestRecordBuilder recordStatus(String recordStatus) {
			addToMd(CoreBibliographicMetadata.MD_RECORD_STATUS, recordStatus);
			return this;
		}

		public IngestRecordBuilder derivedType(String derivedType) {
			addToMd(CoreBibliographicMetadata.MD_DERIVED_TYPE, derivedType);
			return this;
		}

		public IngestRecordBuilder addIdentifier(Consumer<IdentifierBuilder> consumer) {
			identifier(Identifier.build(consumer));
			return this;
		}

		public IngestRecordBuilder addIdentifiers(Identifier... ids) {
			identifiers(List.of(ids));
			return this;
		}

		public IngestRecordBuilder author(Consumer<AuthorBuilder> consumer) {
			author(Author.build(consumer));
			return this;
		}

		public IngestRecordBuilder author(Author author) {
			addToMd(CoreBibliographicMetadata.MD_AUTHOR, author);
			return this;
		}

		public IngestRecordBuilder formOfItem(String formOfItem) {
			addToMd(CoreBibliographicMetadata.MD_FORM_OF_ITEM, formOfItem);
			return this;
		}

		public IngestRecordBuilder derivedFormOfItem(String derivedFormOfItem) {
			addToMd(CoreBibliographicMetadata.MD_DERIVED_FORM_OF_ITEM, derivedFormOfItem);
			return this;
		}

		public IngestRecordBuilder addOtherAuthor(Consumer<AuthorBuilder> consumer) {
			otherAuthor(Author.build(consumer));
			return this;
		}

		// public IngestRecordBuilder sourceRecordUuid(UUID uuid) {
	// 		sourceRecordUuid(uuid);
		// 	return this;
		// }
	}

	public static IngestRecord build(Consumer<IngestRecordBuilder> consumer) {
		IngestRecordBuilder builder = builder();
		consumer.accept(builder);
		return builder.build();
	}
}
