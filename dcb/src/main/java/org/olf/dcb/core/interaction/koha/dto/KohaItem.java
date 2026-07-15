package org.olf.dcb.core.interaction.koha.dto;

import io.micronaut.serde.annotation.Serdeable;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Serdeable
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KohaItem {

	@JsonProperty("item_id")
	private Long itemId;

	@JsonProperty("biblio_id")
	private Long biblioId;

	@JsonProperty("external_id") // Barcode in Koha
	private String externalId;

	@JsonProperty("acquisition_date")
	private String acquisitionDate;

	@JsonProperty("acquisition_source")
	private String acquisitionSource;

	@JsonProperty("bookable")
	private Boolean bookable;

	@JsonProperty("effective_bookable")
	private Boolean effectiveBookable;

	@JsonProperty("home_library_id")
	private String homeLibraryId;

	@JsonProperty("purchase_price")
	private Double purchasePrice;

	@JsonProperty("replacement_price")
	private Double replacementPrice;

	@JsonProperty("replacement_price_date")
	private String replacementPriceDate;

	@JsonProperty("last_checkout_date")
	private String lastCheckoutDate;

	@JsonProperty("last_seen_date")
	private String lastSeenDate;

	@JsonProperty("not_for_loan_status")
	private Integer notForLoanStatus;

	@JsonProperty("effective_not_for_loan_status")
	private Integer effectiveNotForLoanStatus;

	@JsonProperty("damaged_status")
	private Integer damagedStatus;

	@JsonProperty("damaged_date")
	private String damagedDate;

	@JsonProperty("lost_status")
	private Integer lostStatus;

	@JsonProperty("lost_date")
	private String lostDate;

	@JsonProperty("withdrawn")
	private Integer withdrawn;

	@JsonProperty("withdrawn_date")
	private String withdrawnDate;

	@JsonProperty("callnumber")
	private String callnumber;

	@JsonProperty("coded_location_qualifier")
	private String codedLocationQualifier;

	@JsonProperty("checkouts_count")
	private Integer checkoutCount;

	@JsonProperty("renewals_count")
	private Integer renewalsCount;

	@JsonProperty("localuse")
	private Integer localuse;

	@JsonProperty("holds_count")
	private Integer holdCount;

	@JsonProperty("restricted_status")
	private Integer restrictedStatus;

	@JsonProperty("public_notes")
	private String publicNotes;

	@JsonProperty("internal_notes")
	private String internalNotes;

	@JsonProperty("holding_library_id")
	private String holdingLibraryId;

	@JsonProperty("timestamp")
	private String timestamp;

	@JsonProperty("location")
	private String location;

	@JsonProperty("permanent_location")
	private String permanentLocation;

	@JsonProperty("checked_out_date")
	private String checkedOutDate;

	@JsonProperty("call_number_source")
	private String callNumberSource;

	@JsonProperty("call_number_sort")
	private String callNumberSort;

	@JsonProperty("collection_code")
	private String collectionCode;

	@JsonProperty("materials_notes")
	private String materialsNotes;

	@JsonProperty("shelving_control_number")
	private Double shelvingControlNumber;

	@JsonProperty("uri")
	private String uri;

	@JsonProperty("item_type_id")
	private String itemTypeId;

	@JsonProperty("effective_item_type_id")
	private String effectiveItemTypeId;

	@JsonProperty("extended_subfields")
	private String extendedSubfields;

	@JsonProperty("serial_issue_number")
	private String serialIssueNumber;

	@JsonProperty("copy_number")
	private String copyNumber;

	@JsonProperty("inventory_number")
	private String inventoryNumber;

	@JsonProperty("new_status")
	private String newStatus;

	@JsonProperty("exclude_from_local_holds_priority")
	private Boolean excludeFromLocalHoldsPriority;

	@JsonProperty("return_claims")
	private List<Object> returnClaims;

	@JsonProperty("return_claim")
	private Object returnClaim;

	@JsonProperty("home_library")
	private KohaLibrary homeLibrary;

	@JsonProperty("holding_library")
	private KohaLibrary holdingLibrary;

	@JsonProperty("cover_image_ids")
	private List<Object> coverImageIds;

	@JsonProperty("item_group_item")
	private Object itemGroupItem;

	@JsonProperty("serial_item")
	private Object serialItem;

	@JsonProperty("biblio")
	private KohaBiblio biblio;

	@JsonProperty("checkout")
	private KohaCheckout checkout;

	@JsonProperty("transfer")
	private KohaTransfer transfer;

	@JsonProperty("first_hold")
	private KohaHoldRequest firstHold;

	@JsonProperty("recall")
	private KohaRecall recall;

	@JsonProperty("item_type")
	private KohaItemType itemType;

	@JsonProperty("in_bundle")
	private Boolean inBundle;

	@JsonProperty("bundle_host")
	private Object bundleHost;

	@JsonProperty("bundle_items_lost_count")
	private Integer bundleItemsLostCount;

	@JsonProperty("bundle_items_not_lost_count")
	private Integer bundleItemsNotLostCount;

	@JsonProperty("course_item")
	private Object courseItem;

	@JsonProperty("analytics_count")
	private Integer analyticsCount;

	@JsonProperty("_strings")
	private String[] strings;

	@JsonProperty("_status")
	private String[] status;

	@Override
	public String toString() {
		return "KohaItem{" +
			"itemId=" + itemId +
			", biblioId=" + biblioId +
			", externalId='" + externalId + '\'' +
			", holdingLibraryId='" + holdingLibraryId + '\'' +
			", itemTypeId='" + itemTypeId + '\'' +
			", notForLoanStatus=" + notForLoanStatus +
			'}';
	}
}
