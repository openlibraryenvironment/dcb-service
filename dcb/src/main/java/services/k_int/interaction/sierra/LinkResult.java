package services.k_int.interaction.sierra;

import io.micronaut.serde.annotation.Serdeable;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@Serdeable
public class LinkResult {
	String link;
}
