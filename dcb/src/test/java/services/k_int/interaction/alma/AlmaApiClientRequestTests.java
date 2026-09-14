package services.k_int.interaction.alma;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

import reactor.core.publisher.Mono;
import services.k_int.interaction.alma.types.items.AlmaItem;
import services.k_int.interaction.alma.types.items.AlmaItemData;
import services.k_int.interaction.alma.types.items.AlmaItems;

class AlmaApiClientRequestTests {
	@Test
	void shouldPageThroughEveryItemOnABib() {
		final var api = new RecordingAlmaApi(250);

		final var items = api.retrieveAllItems("99123").collectList().block();

		assertThat(items, hasSize(250));
		assertThat(api.offsets, contains(0, 100, 200));
		assertThat(api.lastPath, is("/almaws/v1/bibs/99123/holdings/ALL/items"));
	}

	@Test
	void shouldMakeASingleCallForABibWithNoItems() {
		final var api = new RecordingAlmaApi(0);

		final var items = api.retrieveAllItems("99123").collectList().block();

		assertThat(items, hasSize(0));
		assertThat(api.offsets, contains(0));
	}

	@Test
	void shouldCancelARequestWithoutNotifyingThePatron() {
		final var api = new RecordingAlmaApi(0);

		api.cancelUserRequest("patron-id", "request-id", "PatronNotInterested").block();

		assertThat(api.deleteParams.get("notify_user"), is(false));
		assertThat(api.deleteParams.get("reason"), is("PatronNotInterested"));
	}

	@Test
	void shouldSendNoReasonWhenNoneIsConfigured() {
		final var api = new RecordingAlmaApi(0);

		api.cancelUserRequest("patron-id", "request-id", null).block();

		assertThat(api.deleteParams, not(hasKey("reason")));
		assertThat(api.deleteParams.get("notify_user"), is(false));
	}

	private static class RecordingAlmaApi implements AlmaApiClient {
		private final int totalItems;
		private final List<Integer> offsets = new ArrayList<>();
		private String lastPath;
		private Map<String, Object> deleteParams;

		RecordingAlmaApi(int totalItems) {
			this.totalItems = totalItems;
		}

		@Override
		@SuppressWarnings("unchecked")
		public <T> Mono<T> get(String path, Class<T> responseType, Map<String, Object> queryParams) {
			final int offset = (Integer) queryParams.get("offset");
			final int limit = (Integer) queryParams.get("limit");

			lastPath = path;
			offsets.add(offset);

			final var page = IntStream.range(offset, Math.min(offset + limit, totalItems))
				.mapToObj(n -> AlmaItem.builder()
					.itemData(AlmaItemData.builder().pid(String.valueOf(n)).build())
					.build())
				.toList();

			return Mono.just((T) AlmaItems.builder().recordCount(totalItems).items(page).build());
		}

		@Override
		public Mono<Void> delete(String path, Map<String, Object> queryParams) {
			deleteParams = queryParams;
			return Mono.empty();
		}

		@Override
		public <T> Mono<T> post(String path, Object body, Class<T> responseType, Map<String, Object> queryParams) {
			throw new UnsupportedOperationException();
		}

		@Override
		public <T> Mono<T> post(String path, Object body, Class<T> responseType, Map<String, Object> queryParams,
			String contentType) {

			throw new UnsupportedOperationException();
		}

		@Override
		public <T> Mono<T> put(String path, Object body, Class<T> responseType, Map<String, Object> queryParams) {
			throw new UnsupportedOperationException();
		}

		@Override
		public Mono<Void> authenticateUser(String userId, String password) {
			throw new UnsupportedOperationException();
		}
	}
}
