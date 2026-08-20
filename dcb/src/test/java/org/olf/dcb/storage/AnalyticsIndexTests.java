package org.olf.dcb.storage;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasItems;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.olf.dcb.test.DcbTest;

import io.micronaut.data.r2dbc.operations.R2dbcOperations;
import jakarta.inject.Inject;
import reactor.core.publisher.Flux;

/**
 * Guards V9_0_005__analytics_indexes.sql. Nothing else would notice if it stopped applying -
 * indexes only affect speed, so every other test would still pass while Insights went back to
 * sequentially scanning the two largest tables in the system.
 */
@DcbTest
class AnalyticsIndexTests {

	@Test
	void shouldHaveExactlyOneIndexOnAuditDate() {
		// IF NOT EXISTS matches on NAME, not definition, so a differently-named copy would be
		// built and maintained alongside this one at ~850 MB apiece on a 40M-row table. The
		// Audit Explorer migration wanted its own; this fails if one ever appears.
		assertThat(query("""
			SELECT indexname FROM pg_indexes
			WHERE tablename = 'patron_request_audit'
			  AND indexdef LIKE '%%(audit_date)'
			"""), contains("pra_audit_date_idx"));
	}

	@Test
	void shouldHaveAppliedTheAuditIndexes() {
		assertThat(indexNamesOn("patron_request_audit"), hasItems(
			"pra_audit_date_idx",
			"pra_to_status_idx"));
	}

	@Test
	void shouldHaveAppliedTheRequestStatisticsIndexes() {
		assertThat(indexNamesOn("patron_request"), hasItems(
			"pr_stats_borrower_idx",
			"pr_stats_cluster_idx",
			"pr_stats_pickup_idx",
			"pr_stats_supplier_idx"));
	}

	@Inject
	private R2dbcOperations r2dbcOperations;

	private List<String> indexNamesOn(String table) {
		return query("SELECT indexname FROM pg_indexes WHERE tablename = '%s' ORDER BY 1"
			.formatted(table));
	}

	private List<String> query(String sql) {
		return Flux.from(r2dbcOperations.withConnection(connection ->
				Flux.from(connection.createStatement(sql).execute())
					.flatMap(result -> result.map((row, metadata) -> String.valueOf(row.get(0))))))
			.collectList()
			.block(Duration.ofMinutes(2));
	}
}
