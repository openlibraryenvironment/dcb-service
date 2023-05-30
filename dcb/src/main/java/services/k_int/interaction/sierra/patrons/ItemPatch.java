package services.k_int.interaction.sierra.patrons;

import java.util.List;

import io.micronaut.serde.annotation.Serdeable;
import lombok.Builder;
import lombok.Data;

@Data
@Serdeable
@Builder
public class ItemPatch {
	List<Integer> bibIds;
	Integer itemType;
	String location;
	List<String> barcodes;
}
