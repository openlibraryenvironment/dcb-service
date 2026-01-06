package org.olf.dcb.request.resolution;

import static org.olf.dcb.utils.PropertyAccessUtils.getValueOrNull;
import static reactor.function.TupleUtils.function;
import static services.k_int.utils.ReactorUtils.raiseError;

import java.util.function.Function;

import org.olf.dcb.core.HostLmsService;
import org.olf.dcb.core.model.Item;
import org.reactivestreams.Publisher;
import org.zalando.problem.Problem;

import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@Slf4j
@Singleton
public class SameServerItemFilter implements ItemFilter {
	private final HostLmsService hostLmsService;

	public SameServerItemFilter(HostLmsService hostLmsService) {
		this.hostLmsService = hostLmsService;
	}

	public Function<Item, Publisher<Boolean>> predicate(ItemFilterParameters parameters) {
		final var borrowingHostLmsCode = getValueOrNull(parameters,
			ItemFilterParameters::getBorrowingHostLmsCode);

		return item -> fromSameServer(item, borrowingHostLmsCode);
	}

	private Mono<Boolean> fromSameServer(Item item, String borrowingHostLmsCode) {
		final var itemHostLmsCode = getValueOrNull(item, Item::getHostLmsCode);

		if (itemHostLmsCode == null || borrowingHostLmsCode == null) {
			return raiseError(Problem.builder()
				.withTitle("Missing required value to evaluate item fromSameServer")
				.withDetail("Could not compare LMS codes")
				.with("itemHostLmsCode", itemHostLmsCode)
				.with("borrowingHostLmsCode", borrowingHostLmsCode)
				.build());
		}

		return Mono.zip(hostLmsService.getHostLmsBaseUrl(itemHostLmsCode),
				hostLmsService.getHostLmsBaseUrl(borrowingHostLmsCode))
			.map(function((String itemHostLmsBaseUrl, String borrowingHostLmsBaseUrl) ->
				fromSameServer(itemHostLmsCode, borrowingHostLmsCode,
					itemHostLmsBaseUrl, borrowingHostLmsBaseUrl)));
	}

	private static boolean fromSameServer(String itemHostLmsCode, String borrowingHostLmsCode,
		String itemHostLmsBaseUrl, String borrowingHostLmsBaseUrl) {

		if (itemHostLmsBaseUrl == null || borrowingHostLmsBaseUrl == null) {
			throw Problem.builder()
				.withTitle("Missing required value to evaluate item fromSameServer")
				.withDetail("Could not compare base-url")
				.with("itemHostLmsBaseUrl", itemHostLmsBaseUrl)
				.with("borrowingHostLmsBaseUrl", borrowingHostLmsBaseUrl)
				.build();
		}

		boolean isSameServer = itemHostLmsBaseUrl.equals(borrowingHostLmsBaseUrl);
		boolean isDifferentLms = !itemHostLmsCode.equals(borrowingHostLmsCode);
		boolean shouldExclude = isSameServer && isDifferentLms;

		if (shouldExclude) {
			log.debug("Excluding item from same server: itemLms={}, borrowingLms={}, baseUrl={}",
				itemHostLmsCode, borrowingHostLmsCode, itemHostLmsBaseUrl);
		}

		return !shouldExclude;
	}
}
