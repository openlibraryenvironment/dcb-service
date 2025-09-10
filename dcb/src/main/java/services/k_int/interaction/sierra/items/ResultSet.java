package services.k_int.interaction.sierra.items;

import static java.util.Collections.emptyList;
import static org.olf.dcb.utils.PropertyAccessUtils.getValue;

import java.util.List;

import jakarta.validation.constraints.NotNull;

import io.micronaut.serde.annotation.Serdeable;
import lombok.Builder;
import lombok.Data;

@Data
@Serdeable
@Builder
public class ResultSet {
	int total;
	int start;
	@NotNull List<SierraItem> entries;

	public List<SierraItem> getItems() {
		return getValue(this, ResultSet::getEntries, emptyList());
	}
}
