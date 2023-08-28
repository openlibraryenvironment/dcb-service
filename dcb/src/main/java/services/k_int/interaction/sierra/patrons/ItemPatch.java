package services.k_int.interaction.sierra.patrons;

import io.micronaut.serde.annotation.Serdeable;
import lombok.Builder;
import lombok.Data;
import java.util.List;
import java.util.Map;
import services.k_int.interaction.sierra.FixedField;

@Data
@Serdeable
@Builder
public class ItemPatch {
	List<Integer> bibIds;
	Integer itemType;
	String location;
	String status;
	List<String> barcodes;
        Map<Integer, FixedField> fixedFields;
}
