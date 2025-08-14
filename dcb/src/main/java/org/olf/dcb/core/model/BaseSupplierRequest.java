package org.olf.dcb.core.model;
import io.micronaut.core.annotation.Creator;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.data.annotation.*;
import io.micronaut.data.model.DataType;
import io.micronaut.serde.annotation.Serdeable;
import jakarta.persistence.Column;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;
import lombok.experimental.Accessors;
import lombok.experimental.SuperBuilder;
import org.olf.dcb.request.fulfilment.SupplierRequestStatusCode;
import services.k_int.tests.ExcludeFromGeneratedCoverageReport;

import java.time.Instant;
import java.util.UUID;

@SuperBuilder(toBuilder = true)
@Serdeable
@Data
@ExcludeFromGeneratedCoverageReport
@RequiredArgsConstructor(onConstructor_ = @Creator())
@AllArgsConstructor
@ToString(onlyExplicitlyIncluded = true)
@Accessors(chain = true)
public abstract class BaseSupplierRequest<T extends BaseSupplierRequest<T>> {
	@Nullable
	@DateCreated
	private Instant dateCreated;

	@Nullable
	@DateUpdated
	private Instant dateUpdated;

	@Nullable
	@Relation(value = Relation.Kind.MANY_TO_ONE)
	@Column(name = "patron_request_id")
	private PatronRequest patronRequest;

	@NotNull
	@NonNull
	@Size(max = 200)
	private String localItemId;

	@Size(max = 200)
	private String localBibId;

	@Nullable
	@Size(max = 200)
	private String localHoldingId;

	@Nullable
	@Size(max = 200)
	private String localItemBarcode;

	@Nullable
	@Size(max = 200)
	private String localItemLocationCode;

	@ToString.Include
	@Nullable
	@Size(max = 32)
	private String localItemStatus;

	@ToString.Include
	@Nullable
	@Size(max = 32)
	private String rawLocalItemStatus;

	@Nullable
	Instant localItemLastCheckTimestamp;

	@Nullable
	private Long localItemStatusRepeat;

	@Nullable
	@Size(max = 32)
	private String localItemType;

	@Nullable
	@Size(max = 32)
	private String canonicalItemType;

	@NotNull
	@NonNull
	@Size(max = 200)
	private String hostLmsCode;

	@ToString.Include
	@Nullable
	@Enumerated(EnumType.STRING)
	private SupplierRequestStatusCode statusCode;

	@Nullable
	@Relation(value = Relation.Kind.MANY_TO_ONE)
	private PatronIdentity virtualIdentity;

	@Nullable
	@Size(max = 200)
	private String localId;

	@ToString.Include
	@Nullable
	@Size(max = 32)
	private String localStatus;

	@ToString.Include
	@Nullable
	@Size(max = 200)
	private String rawLocalStatus;

	@Nullable
	Instant localRequestLastCheckTimestamp;

	@Nullable
	private Long localRequestStatusRepeat;

	@ToString.Include
	@Nullable
	private String localAgency;

	@ToString.Include
	@Nullable
	@Relation(value = Relation.Kind.MANY_TO_ONE)
	private DataAgency resolvedAgency;

	@Nullable
	private String protocol;

  @Builder.Default
	private Integer localRenewalCount = Integer.valueOf(0);

  @Builder.Default
	private Boolean localRenewable = Boolean.TRUE;

  @Builder.Default
	private Integer localHoldCount = Integer.valueOf(0);

	public T setDateCreated(@Nullable Instant dateCreated) {
		this.dateCreated = dateCreated;
		return (T) this;
	}

	public T setDateUpdated(@Nullable Instant dateUpdated) {
		this.dateUpdated = dateUpdated;
		return (T) this;
	}

	public T setPatronRequest(@Nullable PatronRequest patronRequest) {
		this.patronRequest = patronRequest;
		return (T) this;
	}

	public T setLocalItemId(@NotNull @NonNull @Size(max = 200) String localItemId) {
		this.localItemId = localItemId;
		return (T) this;
	}

	public T setLocalBibId(@Size(max = 200) String localBibId) {
		this.localBibId = localBibId;
		return (T) this;
	}

	public T setLocalHoldingId(@Size(max = 200) String localHoldingId) {
		this.localHoldingId = localHoldingId;
		return (T) this;
	}

	public T setLocalItemBarcode(@Nullable @Size(max = 200) String localItemBarcode) {
		this.localItemBarcode = localItemBarcode;
		return (T) this;
	}

	public T setLocalItemLocationCode(@Nullable @Size(max = 200) String localItemLocationCode) {
		this.localItemLocationCode = localItemLocationCode;
		return (T) this;
	}

	public T setLocalItemStatus(@Nullable @Size(max = 32) String localItemStatus) {
		this.localItemStatus = localItemStatus;
		return (T) this;
	}

	public T setRawLocalItemStatus(@Nullable @Size(max = 32) String rawLocalItemStatus) {
		this.rawLocalItemStatus = rawLocalItemStatus;
		return (T) this;
	}

	public T setLocalItemLastCheckTimestamp(@Nullable Instant localItemLastCheckTimestamp) {
		this.localItemLastCheckTimestamp = localItemLastCheckTimestamp;
		return (T) this;
	}

	public T setLocalItemStatusRepeat(@Nullable Long localItemStatusRepeat) {
		this.localItemStatusRepeat = localItemStatusRepeat;
		return (T) this;
	}

	public T setLocalItemType(@Nullable @Size(max = 32) String localItemType) {
		this.localItemType = localItemType;
		return (T) this;
	}

	public T setCanonicalItemType(@Nullable @Size(max = 32) String canonicalItemType) {
		this.canonicalItemType = canonicalItemType;
		return (T) this;
	}

	public T setHostLmsCode(@NotNull @NonNull @Size(max = 200) String hostLmsCode) {
		this.hostLmsCode = hostLmsCode;
		return (T) this;
	}

	public T setStatusCode(@Nullable SupplierRequestStatusCode statusCode) {
		this.statusCode = statusCode;
		return (T) this;
	}

	public T setVirtualIdentity(@Nullable PatronIdentity virtualIdentity) {
		this.virtualIdentity = virtualIdentity;
		return (T) this;
	}

	public T setLocalId(@Nullable @Size(max = 200) String localId) {
		this.localId = localId;
		return (T) this;
	}

	public T setLocalStatus(@Nullable @Size(max = 32) String localStatus) {
		this.localStatus = localStatus;
		return (T) this;
	}

	public T setRawLocalStatus(@Nullable @Size(max = 200) String rawLocalStatus) {
		this.rawLocalStatus = rawLocalStatus;
		return (T) this;
	}

	public T setLocalRequestLastCheckTimestamp(@Nullable Instant localRequestLastCheckTimestamp) {
		this.localRequestLastCheckTimestamp = localRequestLastCheckTimestamp;
		return (T) this;
	}

	public T setLocalRequestStatusRepeat(@Nullable Long localRequestStatusRepeat) {
		this.localRequestStatusRepeat = localRequestStatusRepeat;
		return (T) this;
	}

	public T setLocalAgency(@Nullable String localAgency) {
		this.localAgency = localAgency;
		return (T) this;
	}

	public T setResolvedAgency(@Nullable DataAgency resolvedAgency) {
		this.resolvedAgency = resolvedAgency;
		return (T) this;
	}

	public T setProtocol(@Nullable String protocol) {
		this.protocol = protocol;
		return (T) this;
	}

	public T setLocalRenewalCount(Integer localRenewalCount) {
		this.localRenewalCount = localRenewalCount;
		return (T) this;
	}

  public T setLocalHoldCount(Integer localHoldCount) { 
    this.localHoldCount = localHoldCount;
    return (T) this;
	}

}
