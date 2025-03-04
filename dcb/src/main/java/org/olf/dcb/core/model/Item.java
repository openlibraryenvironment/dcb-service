package org.olf.dcb.core.model;

import static java.util.Comparator.comparing;
import static java.util.Comparator.naturalOrder;
import static java.util.Comparator.nullsLast;
import static org.olf.dcb.core.model.ItemStatusCode.AVAILABLE;
import static org.olf.dcb.utils.PropertyAccessUtils.getValue;
import static org.olf.dcb.utils.PropertyAccessUtils.getValueOrNull;

import java.time.Instant;
import java.util.Comparator;
import java.util.Optional;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Serdeable
@Builder(toBuilder = true)
@AllArgsConstructor()
@Accessors(chain=true)
public class Item implements Comparable<Item> {
	private String localId;
	private ItemStatus status;
	@Nullable
	private Instant dueDate;
	private Location location;
	private String barcode;
	private String callNumber;
	private Boolean isRequestable;
	private Integer holdCount;
	private String localBibId;
	private String localItemType;
	private String localItemTypeCode;
	private String canonicalItemType;
	private Boolean deleted;
	private Boolean suppressed;
	private DataAgency agency;
	private String owningContext;
	private Instant availableDate;

	// If this item has attached volume information use these two fields to stash the raw
	// and the processed volume statement. parsed volume statement
	private String rawVolumeStatement;
	private String parsedVolumeStatement;

	/** A shelving location. A classifier that is shared throughout all branches in a library and
	 * infers a particular meaning on the item (But not a physical location) - for example all branch libraries
	 * can have "Reference" shelving location. Shelving location is not a synonm for or alternative to "Location"
	 * An item will always have a "Location" (E.g. central library, some-street-branch) and can optionally
	 * have a "shelving location" (Reference, Best Sellers, Childrens). Knowing an item is shelved in "Reference"
	 * Will allow us to infer some additional rules about loan policies, but it is absolutely not 
	 * the same concept as "Location" which indicates to us which branch an item is housed at.
	 */
	@Nullable
	private String shelvingLocation;

	/* Loan policy carries an infered loan polict whereby the host ILS client will calculate, based on
	 * system specific fields and host specific configuration, what the canonical consortial lending loan
	 * policy is for this item. Items can be "Available" but not lendable to patrons
	 */
	@Builder.Default
	private DerivedLoanPolicy derivedLoanPolicy = DerivedLoanPolicy.UNKNOWN;

	public boolean notSuppressed() {
		return suppressed == null || !suppressed;
	}

	public boolean notDeleted() {
		return deleted == null || !deleted;
	}

	public boolean isAvailable() {
		return status.getCode() == AVAILABLE;
	}

	public boolean hasNoHolds() {
		return holdCount == null || holdCount == 0;
	}

	public String getLocationCode() {
		return getValueOrNull(location, Location::getCode);
	}

	public boolean hasAgency() {
		return getAgencyCode() != null;
	}

	public boolean AgencyIsSupplying() {
		return getValue(agency, Agency::getIsSupplyingAgency, false);
	}

	public String getAgencyCode() {
		return getValueOrNull(agency, Agency::getCode);
	}

	public boolean hasHostLms() {
		return getHostLmsCode() != null;
	}

	public HostLms getHostLms() {
		return getValueOrNull(agency, Agency::getHostLms);
	}

	public String getHostLmsCode() {
		return getValueOrNull(getHostLms(), HostLms::getCode);
	}

	@Override
	public int compareTo(Item other) {
		return nullsLast(CompareByLocationCodeThenCallNumber())
			.compare(this, other);
	}

	private Comparator<Item> CompareByLocationCodeThenCallNumber() {
		return comparing(Item::getLocationCode, nullsLast(naturalOrder()))
			.thenComparing(Item::getCallNumber, nullsLast(naturalOrder()));
	}

	public static Item setOwningContext(Item item) {
		return Optional.ofNullable(getValueOrNull(item, Item::getHostLmsCode))
			.map(code -> {
				item.owningContext = code;
				return item;
			})
			.orElse(item);
	}
}
