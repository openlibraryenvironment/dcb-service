package services.k_int.interaction.sierra.items;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;
import lombok.Builder;
import lombok.Data;
import services.k_int.interaction.sierra.FixedField;
import services.k_int.interaction.sierra.VarField;

import java.util.*;

@Data
@Builder
@Serdeable
public class SierraItem {

	public static final List<String> SIERRA_ITEM_FIELDS = List.of(
		"id",
		"updatedDate",
		"createdDate",
		"deletedDate",
		"deleted",
		"suppressed",
		"bibIds",
		"location",
		"status",
		"volumes",
		"barcode",
		"callNumber",
		"itemType",
		"transitInfo",
		"copyNo",
		"holdCount",
		"fixedFields",
		"varFields"
	);

	@Nullable
	String id;
	@Nullable
	String updatedDate;
	@Nullable
	String createdDate;
	@Nullable
	String deletedDate;
	@Nullable
	Boolean deleted;
	@Nullable
	Boolean suppressed;
	@Nullable
	String[] bibIds;
	@Nullable
	Location location;
	@Nullable
	Status status;
	@Nullable
	String[] volumes;
	@Nullable
	String barcode;
	@Nullable
	String callNumber;
	@Nullable
	String itemType;
	@Nullable
	TransitInfo transitInfo;
	@Nullable
	Integer copyNo;
	@Nullable
	Integer holdCount;
	@Nullable
  Map<Integer, FixedField> fixedFields;
	@Nullable
	List<VarField> varFields;

	public Map<String, Object> toMap() {
		return new HashMap<String, Object>() {{
			put("id", id);
			put("updatedDate", updatedDate);
			put("createdDate", createdDate);
			put("deletedDate", deletedDate);
			put("deleted", deleted);
			put("suppressed", suppressed);
			put("bibIds", bibIds);
			put("location", location);
			put("status", status);
			put("volumes", volumes);
			put("barcode", barcode);
			put("callNumber", callNumber);
			put("itemType", itemType);
			put("transitInfo", transitInfo);
			put("copyNo", copyNo);
			put("holdCount", holdCount);
			put("fixedFields", fixedFields);
			put("varFields", varFields);
		}};
	}
}
