package services.k_int.interaction.sierra;

import io.micronaut.serde.annotation.Serdeable;
import lombok.Data;

@Data
@Serdeable
public class LinkResult {
	String link;
}
