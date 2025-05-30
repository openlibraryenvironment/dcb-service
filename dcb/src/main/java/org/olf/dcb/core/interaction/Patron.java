package org.olf.dcb.core.interaction;

import static org.olf.dcb.utils.PropertyAccessUtils.getValue;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Objects;

import io.micronaut.core.annotation.Creator;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import services.k_int.tests.ExcludeFromGeneratedCoverageReport;

@Builder
@Data
@Accessors(chain=true)
@NoArgsConstructor(onConstructor_ = @Creator())
@AllArgsConstructor
@Serdeable
@ExcludeFromGeneratedCoverageReport
public class Patron {

	// A patron record fetched from a system like sierra can have many "ID" entries.. record them here
	private List<String> localId;
	private List<String> localNames;
	private List<String> localBarcodes;
	private List<String> uniqueIds;
	private String localPatronType;
	private String localHomeLibraryCode;
	private String hostLmsCode;

	// Because different ILS systems will have different needs for converting local patron type
	// into a canonical type, Sierra for example uses a numeric range mapping, we should hide the
	// details of how we convert a local patron type to a canonical type in the HostILS implementation
	// and pass back the mapped type here, rather than forcing the caller to know how to convert.
	private String canonicalPatronType;
	private Date expiryDate; // To be formatted as needed by any system


	// These two seem to be used to carry data  in a very specific Polaris flow... Ideally move out. 
	private String localItemId;
	private Integer localItemLocationId;

	private Boolean isDeleted;
	@Nullable private Boolean isBlocked;
	private String city;
	private String postalCode;
	private String state;
	private Boolean isActive;

	public boolean isEligible() {
		return !Objects.equals(canonicalPatronType, "NOT_ELIGIBLE");
	}

	public boolean isNotDeleted() {
		return isDeleted == null || !isDeleted;
	}

	public String getFirstLocalId() {
		return getValue(this, Patron::getLocalId, new ArrayList<String>())
			.stream()
			.findFirst()
			.orElse(null);
	}

	public String getFirstBarcode() {
		return getFirstBarcode(null);
	}

	public String getFirstBarcode(String defaultBarcode) {
		return getValue(this, Patron::getLocalBarcodes, new ArrayList<String>())
			.stream()
			.findFirst()
			.orElse(defaultBarcode);
	}
}
