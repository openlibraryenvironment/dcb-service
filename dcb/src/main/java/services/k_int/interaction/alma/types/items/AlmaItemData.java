package services.k_int.interaction.alma.types.items;

import io.micronaut.serde.annotation.Serdeable;
import com.fasterxml.jackson.annotation.JsonRootName;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

import services.k_int.interaction.alma.types.CodeValuePair;
import services.k_int.interaction.alma.types.WithAttr;
import services.k_int.interaction.alma.types.holdings.AlmaHolding;

// https://developers.exlibrisgroup.com/alma/apis/docs/xsd/rest_users.xsd
// https://developers.exlibrisgroup.com/alma/apis/bibs/

@Data
@AllArgsConstructor
@Builder
@Serdeable
public class AlmaItemData {
	@JsonProperty("pid")
	String pid;
	@JsonProperty("barcode")
	String barcode;
	@JsonProperty("creation_date")
	String creationDate;
	@JsonProperty("modification_date")
	String modificationDate;
	@JsonProperty("base_status")
	CodeValuePair baseStatus;
	@JsonProperty("physical_material_type")
	CodeValuePair physicalMaterialType;
	@JsonProperty("awaiting_reshelving")
	String awaitingReshelving;
	@JsonProperty("reshelving_time")
	String reshelvingTime;
	@JsonProperty("item_policy")
	CodeValuePair itemPolicy;
	@JsonProperty("policy")
	CodeValuePair policy;
	@JsonProperty("provenance")
	CodeValuePair provenance;
	@JsonProperty("po_line")
	String poLine;
	@JsonProperty("issue_date")
	String issue_date;
	@JsonProperty("is_magnetic")
	Boolean is_magnetic;
	@JsonProperty("arrival_date")
	String arrivalDate;
	@JsonProperty("expected_arrival_date")
	String expectedArrivalDate;
	@JsonProperty("year_of_issue")
	String yearOfIssue;
	@JsonProperty("enumeration_a")
	String enumerationA;
	@JsonProperty("enumeration_b")
	String enumerationB;
	@JsonProperty("enumeration_c")
	String enumerationC;
	@JsonProperty("enumeration_d")
	String enumerationD;
	@JsonProperty("enumeration_e")
	String enumerationE;
	@JsonProperty("enumeration_f")
	String enumerationF;
	@JsonProperty("enumeration_g")
	String enumerationG;
	@JsonProperty("enumeration_h")
	String enumerationH;
	@JsonProperty("enumeration_i")
	String enumerationI;
	@JsonProperty("enumeration_j")
	String enumerationJ;
	@JsonProperty("enumeration_k")
	String enumerationK;
	@JsonProperty("enumeration_l")
	String enumerationL;
	@JsonProperty("enumeration_m")
	String enumerationM;
	@JsonProperty("breaking_indicator")
	CodeValuePair breakingIndicator;
	@JsonProperty("pattern_type")
	CodeValuePair patternType;
	@JsonProperty("linking_number")
	String linkingNumber;
	@JsonProperty("description")
	String description;
	@JsonProperty("replacement_cost")
	String replacementCost;
	@JsonProperty("receiving_operator")
	String receivingOperator;
	@JsonProperty("process_type")
	CodeValuePair process_type;
	@JsonProperty("inventory_number")
	String inventoryNumber;
	@JsonProperty("inventory_date")
	String inventoryDate;
	@JsonProperty("inventory_price")
	String inventoryPrice;
	@JsonProperty("recieve_number")
	String receiveNumber;
	@JsonProperty("weeding_number")
	String weedingNumber;
	@JsonProperty("weeding_date")
	String weedingDate;
	@JsonProperty("library")
	CodeValuePair library;
	@JsonProperty("location")
	CodeValuePair location;
	@JsonProperty("alternative_call_number")
	String alternativeCallNumber;
	@JsonProperty("alternative_call_number_type")
	WithAttr alternativeCallNumberType;
	@JsonProperty("alt_number_source")
	String altNumberSource;
	@JsonProperty("storage_location_id")
	String storageLocationId;
	@JsonProperty("pages")
	String pages;
	@JsonProperty("pieces")
	String pieces;
	@JsonProperty("public_note")
	String publicNote;
	@JsonProperty("fulfillment_note")
	String fulfillmentNote;
	@JsonProperty("internal_note_1")
	String internalNote1;
	@JsonProperty("internal_note_2")
	String internalNote2;
	@JsonProperty("internal_note_3")
	String internalNote3;
	@JsonProperty("statistics_note_1")
	String statisticsNote1;
	@JsonProperty("statistics_note_2")
	String statisticsNote2;
	@JsonProperty("statistics_note_3")
	String statisticsNote3;
	@JsonProperty("physical_condition")
	WithAttr physicalCondition;
	@JsonProperty("committed_to_retain")
	WithAttr committedToRetain;
	@JsonProperty("retention_reason")
	WithAttr retentionReason;
	@JsonProperty("retention_note")
	String retentionNote;
	@JsonProperty("holding_data")
	AlmaHolding holdingData;
}
