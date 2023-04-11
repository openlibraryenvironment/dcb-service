package services.k_int.interaction.sierra.items;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;

@Serdeable
@Builder
// Needs to be public so can be used for testing
@RequiredArgsConstructor(access = AccessLevel.PUBLIC)
@Data
public class Status {
	@Nullable
	final String code;
	@Nullable
	final String display;
	@Nullable
	final String duedate;
}
