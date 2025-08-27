package org.olf.dcb.core.model;

import static io.micronaut.core.util.CollectionUtils.isEmpty;
import static org.olf.dcb.utils.PropertyAccessUtils.getValueOrNull;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import io.micronaut.core.annotation.Creator;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.data.annotation.DateCreated;
import io.micronaut.data.annotation.DateUpdated;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.Transient;
import io.micronaut.data.annotation.TypeDef;
import io.micronaut.data.model.DataType;
import io.micronaut.serde.annotation.Serdeable;
import jakarta.persistence.OneToMany;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import services.k_int.tests.ExcludeFromGeneratedCoverageReport;

/**
 * A patron is the canonical record that links together all the different patron
 * identities for a user across the network.
 */
@Builder
@Data
@NoArgsConstructor(onConstructor_ = @Creator())
@AllArgsConstructor
@Serdeable
@MappedEntity
@ExcludeFromGeneratedCoverageReport
public class Patron {
	@NotNull
	@NonNull
	@Id
	@TypeDef( type = DataType.UUID)
	private UUID id;

	@ToString.Exclude
	@Nullable
	@DateCreated
	private Instant dateCreated;

	@ToString.Exclude
	@Nullable
	@DateUpdated
	private Instant dateUpdated;

	@Nullable
	@Size(max = 200)
	private String homeLibraryCode;

	@ToString.Exclude
	@OneToMany(mappedBy = "patronId")
	private List<PatronIdentity> patronIdentities;

	@Transient
	public Optional<PatronIdentity> getHomeIdentity() {
		if (isEmpty(patronIdentities)) {
			return Optional.empty();
		}

		return patronIdentities.stream()
			.filter(PatronIdentity::getHomeIdentity)
			.findFirst();
	}

	public boolean hasNoIdentities() {
		return isEmpty(patronIdentities);
	}

	@Transient
	@Nullable
	public String determineUniqueId() {
		// This property is called determine instead of get to avoid the exceptions
		// causing a Jakarta validation exception
		return getHomeIdentity()
			.map(identity -> {
				if (identity.getResolvedAgency() == null) {
					throw new RuntimeException(
						"No resolved agency for patron " + getId() +
							"homeLibraryCode was " + getHomeLibraryCode());
				}

				return identity.getLocalId() + "@" + identity.getResolvedAgency().getCode();
			})
			.orElseThrow(() -> new NoHomeIdentityException(id, patronIdentities));
	}

	@Transient
	public PatronIdentity determineHomeIdentity() {
		// This property is called determine instead of get to avoid the exceptions
		// causing a Jakarta validation exception
		return getHomeIdentity()
			.orElseThrow(() -> new NoHomeIdentityException(id, patronIdentities));
	}

	@Transient
	@Nullable
	public String determineHomeIdentityBarcode() {
		// This property is called determine instead of get to avoid the exceptions
		// causing a Jakarta validation exception
		return getHomeIdentity()
			.map(PatronIdentity::getLocalBarcode)
			.orElseThrow(() -> new NoHomeBarcodeException(id));
	}

	@Transient
	public String determineBorrowingAgencyCode() {
		final var homeIdentity = determineHomeIdentity();

		final var borrowingAgency = getValueOrNull(homeIdentity, PatronIdentity::getResolvedAgency);

		return getValueOrNull(borrowingAgency, DataAgency::getCode);
	}
}
