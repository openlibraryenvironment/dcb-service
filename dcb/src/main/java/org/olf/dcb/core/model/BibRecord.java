package org.olf.dcb.core.model;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.olf.dcb.core.model.clustering.ClusterRecord;
import org.olf.dcb.core.model.clustering.CoreBibliographicMetadata;
import org.olf.dcb.ingest.model.Author;

import io.micronaut.core.annotation.Creator;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.data.annotation.DateCreated;
import io.micronaut.data.annotation.DateUpdated;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.MappedProperty;
import io.micronaut.data.annotation.Relation;
import io.micronaut.data.annotation.Transient;
import io.micronaut.data.annotation.TypeDef;
import io.micronaut.data.model.DataType;
import io.micronaut.serde.annotation.Serdeable;
import jakarta.persistence.Column;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.Singular;
import lombok.experimental.Accessors;
import services.k_int.tests.ExcludeFromGeneratedCoverageReport;


@Builder(toBuilder = true)
@Data
@AllArgsConstructor
@Serdeable
@MappedEntity
@ExcludeFromGeneratedCoverageReport
@Accessors(chain = true)
public class BibRecord implements CoreBibliographicMetadata {
	
	@Creator()
  public BibRecord() {
  	 canonicalMetadata = new HashMap<>();
  }
	
	@NotNull
	@NonNull
	@Id
	@TypeDef(type = DataType.UUID)
	private UUID id;

	@Nullable
	@DateCreated
	private Instant dateCreated;

	@Nullable
	@DateUpdated
	private Instant dateUpdated;

	@NotNull
	@NonNull
	@TypeDef(type = DataType.UUID)
	private UUID sourceSystemId;

	@NotNull
	@NonNull
	@Size(max = 256)
	private String sourceRecordId;
	
	// might have to think about adding serialize = false to @Relation to prevent cycles
//	@NonNull
//	@NotNull
	@Relation(value = Relation.Kind.MANY_TO_ONE)
	@Column(name = "contributes_to")
	private ClusterRecord contributesTo;

	// A note about why we made the clustering decision we made
	@Nullable
	private String clusterReason;

	@Nullable
	private String typeOfRecord;

	// Generate a string which might be useful in blocking titles
	// for stage one of deduplication
	@Nullable
	private String blockingTitle;

	@Singular("canonicalMetadata")
	@NonNull
	@NotNull
	@MappedProperty(type = DataType.JSON)
	Map<String, Object> canonicalMetadata;

	// Allocate a score to this record indicating it's semantic density in order to
	// choose the
	// best member of a cluster for presentation to the user
	@Nullable
	Integer metadataScore;

	// When we process a source record to produce a bibRecord we can now track what
	// version of the
	// process was applied. This gives us a way to know which records need to be
	// reprocessed when we
	// updgrade a system.
	@Nullable
	Integer processVersion;

  // Added to allow us to point directly to the source record that gave rise to this bib, so we can 
  // mark it needs reporocessing in some scenarios
	@Nullable
  UUID sourceRecordUuid;

//	@Override
//	@Nullable
//	public String getDerivedType() {
//		return CoreBibliographicMetadata.super.getDerivedType();
//	}

//	@Override
//	@Nullable
//	public String getRecordStatus() {
//		return CoreBibliographicMetadata.super.getRecordStatus();
//	}
	
	@Override
	@Nullable
	@TypeDef(type = DataType.STRING)
	public String getTitle() {
		return CoreBibliographicMetadata.super.getTitle();
	}

	@Override
	@Transient
	@Nullable
	public Author getAuthor() {
		return CoreBibliographicMetadata.super.getAuthor();
	}

	@Override
	@Transient
	@Nullable
	public String getPlaceOfPublication() {
		return CoreBibliographicMetadata.super.getPlaceOfPublication();
	}

  @Override
  @Transient
  @Nullable
  public String getFormOfItem() {
    return CoreBibliographicMetadata.super.getFormOfItem();
  }

  @Override
  @Transient
  @Nullable
  public String getDerivedFormOfItem() {
    return CoreBibliographicMetadata.super.getDerivedFormOfItem();
  }

	@Override
	@Transient
	@Nullable
	public String getPublisher() {
		return CoreBibliographicMetadata.super.getPublisher();
	}

	@Override
	@Transient
	@Nullable
	public String getDateOfPublication() {
		return CoreBibliographicMetadata.super.getDateOfPublication();
	}

	@Override
	@Transient
	@Nullable
	public String getEdition() {
		return CoreBibliographicMetadata.super.getEdition();
	}

	@Override
	@Transient
	public boolean isLargePrint() {
		// Return type of boolean is incompatible with null, supply default.
		return CoreBibliographicMetadata.super.isLargePrint();
	}
	
	public static class BibRecordBuilder {
		public BibRecordBuilder title(String title) {
			this.canonicalMetadata(MD_TITLE, title);
			return this;
		}
		public BibRecordBuilder recordStatus(String recordStatus) {
			this.canonicalMetadata(MD_RECORD_STATUS, recordStatus);
			return this;
		}
		public BibRecordBuilder derivedType(String derivedType) {
			this.canonicalMetadata(MD_DERIVED_TYPE, derivedType);
			return this;
		}
	}
}
