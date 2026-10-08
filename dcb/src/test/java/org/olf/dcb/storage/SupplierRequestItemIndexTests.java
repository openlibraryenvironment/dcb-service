package org.olf.dcb.storage;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.olf.dcb.test.DcbTest;

import io.micronaut.data.r2dbc.operations.R2dbcOperations;
import jakarta.inject.Inject;
import reactor.core.publisher.Flux;

/** Guards the supplier_request_item_index migration: an index changes only speed, so nothing else would notice it missing. */
@DcbTest
class SupplierRequestItemIndexTests {
	@Inject
	private R2dbcOperations r2dbcOperations;

	@Test
	void shouldIndexActiveSupplierRequestsBySupplierCopy() {
		assertThat(query("""
			SELECT indexdef FROM pg_indexes
			WHERE tablename = 'supplier_request' AND indexname = 'idx_supplier_request_item'
			"""), contains(
				"CREATE INDEX idx_supplier_request_item ON public.supplier_request USING btree (host_lms_code, local_item_id) WHERE is_active"));
	}

	private List<String> query(String sql) {
		return Flux.from(r2dbcOperations.withConnection(connection ->
				Flux.from(connection.createStatement(sql).execute())
					.flatMap(result -> result.map((row, metadata) -> String.valueOf(row.get(0))))))
			.collectList()
			.block(Duration.ofMinutes(2));
	}
}
