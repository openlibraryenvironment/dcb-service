package org.olf.dcb.core.model;

import java.sql.Blob;
import java.time.LocalDate;
import java.util.UUID;

import co.elastic.clients.elasticsearch.xpack.usage.Audit;
import io.micronaut.core.annotation.Creator;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;

import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.TypeDef;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.Relation;

import io.micronaut.security.annotation.UpdatedBy;
import jakarta.validation.constraints.Size;

import io.micronaut.data.model.DataType;
import io.micronaut.serde.annotation.Serdeable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import org.olf.dcb.core.audit.Auditable;
import services.k_int.tests.ExcludeFromGeneratedCoverageReport;
import lombok.ToString;

@Data
@Accessors(chain=true)
@Serdeable
@ExcludeFromGeneratedCoverageReport
@NoArgsConstructor(onConstructor_ = @Creator())
@AllArgsConstructor
@Builder
@ToString(onlyExplicitlyIncluded = true)
@MappedEntity
public class Consortium implements Auditable {
	@ToString.Include
	@NonNull
	@Id
	@TypeDef( type = DataType.UUID)
	private UUID id;

	@NonNull
	@Size(max = 200)
	private String name;

	@NonNull
	@Size(max = 200)
	private String displayName;

	@Nullable
	private LocalDate dateOfLaunch;

	@Relation(value = Relation.Kind.ONE_TO_ONE)
	@Nullable
	private LibraryGroup libraryGroup;

	@Nullable
	@Size(max = 200)
	private String websiteUrl;

	@Nullable
	@Size(max = 200)
	private String catalogueSearchUrl;

	@Nullable
	@Size(max = 400)
	private String description;

	@Nullable
	@UpdatedBy
	private String lastEditedBy;

	@Nullable
	private String reason;

	@Nullable
	private String changeCategory;

	@Nullable
	private String changeReferenceUrl;

	@Nullable
	private String headerImageUrl; // Image for DCB Admin app header, 36x36

	@Nullable
	private String headerImageUploader; // Info about upload

	@Nullable
	private String headerImageUploaderEmail; // Info about upload

	@Nullable
	private String aboutImageUrl; // Image for "About" section, 48x48

	@Nullable
	private String aboutImageUploader; // Image for "About" section, 48x48

	@Nullable
	private String aboutImageUploaderEmail; // Info about upload

}
