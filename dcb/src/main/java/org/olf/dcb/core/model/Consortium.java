package org.olf.dcb.core.model;

import java.time.LocalDate;
import java.util.UUID;

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

/** <p> A class representing the Consortium entity within DCB. This class contains information about a consortium.
 * </p><br>
 * <p>A consortium may have a one-to-many relationship with its functional settings.
 * It must have a one-to-one relationship with a LibraryGroup of type "Consortium", which will hold the associated libraries.
 * </p><br>
 * At the time of writing, only one Consortium is intended to exist within a DCB instance.
 * To find a consortium's functional settings, obtain the consortium through its repository methods
 * and then call the FunctionalSettingRepository's 'findByConsortium' method.
 * */
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
