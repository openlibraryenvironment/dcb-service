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
 * At the time of writing, only one Consortium can exist within a DCB instance: attempting to create a second will result in an error.
 * To find a consortium's functional settings, obtain the consortium through its repository methods
 * and then call the ConsortiumFunctionalRepository's 'findByConsortium' method.
 * Please see the getFunctionalSettingsForConsortiumDataFetcher for an example of how this can be done.
 * It can be found in DataFetchers.java.
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

	// --- Patron-facing brand (N-1.3) -------------------------------------------
	//
	// Distinct from headerImageUrl (36x36) and aboutImageUrl (48x48), which are
	// dcb-admin-ui chrome icons. This is the mark a patron sees in the discovery
	// app's brand lockup, and the two are different assets at different sizes.

	@Nullable
	@Size(max = 400)
	private String brandLogoUrl;

	@Nullable
	@Size(max = 255)
	private String brandLogoAlt;

	/**
	 * A SQUARE mark, for the app bar and the favicon (R-17d).
	 *
	 * Its own field rather than a rendering hint on {@link #brandLogoUrl}, because a
	 * lockup needs horizontal room and a 32px box does not have any: sharing the column
	 * produces a squashed lockup in the chrome of every page.
	 */
	@Nullable
	@Size(max = 400)
	private String brandHeaderIconUrl;

	/**
	 * The canvas behind the discovery app's landing hero (R-17d).
	 *
	 * Consortium level only, and there is deliberately no library equivalent: a mark
	 * identifies an organisation and belongs at every level of the brand chain, a canvas
	 * does not. Text over it renders on a token scrim, so the frontend's contrast gate
	 * keeps measuring a token rather than a photograph an administrator uploaded.
	 */
	@Nullable
	@Size(max = 400)
	private String brandBackgroundImageUrl;

	/** Patron-facing copy. NOT {@link #description}, which is staff-facing prose. */
	@Nullable
	@Size(max = 500)
	private String patronWelcome;

	/**
	 * A theme from the discovery app's registry, not a colour. Validated on write
	 * against a configured vocabulary and tolerated on read — an unrecognised name
	 * falls back to the default brand rather than breaking the patron app.
	 */
	@Nullable
	@Size(max = 64)
	private String defaultThemeName;

}
