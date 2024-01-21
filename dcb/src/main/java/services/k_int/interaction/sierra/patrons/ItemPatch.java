package services.k_int.interaction.sierra.patrons;

import io.micronaut.serde.annotation.Serdeable;
import lombok.Builder;
import lombok.Data;
import java.util.List;
import java.util.Map;
import services.k_int.interaction.sierra.FixedField;
import services.k_int.interaction.sierra.VarField;
import io.micronaut.core.annotation.Nullable;


@Data
@Serdeable
@Builder
public class ItemPatch {
	List<Integer> bibIds;
	Integer itemType;
	String location;
	String status;
	String itemMessage;
	List<String> messages;
	List<String> barcodes;
	@Nullable Map<Integer, FixedField> fixedFields;
	@Nullable List<VarField> varFields;
}
