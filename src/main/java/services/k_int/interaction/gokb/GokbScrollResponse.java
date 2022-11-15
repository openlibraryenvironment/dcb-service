package services.k_int.interaction.gokb;

import java.util.List;

import io.micronaut.serde.annotation.Serdeable;

@Serdeable
public record GokbScrollResponse(
		String result,
		int scrollSize,
		int lastPage,
		String scrollId,
		boolean hasMoreRecords,
		int size,
		int total,
		List<GokbTipp> records) {
}
