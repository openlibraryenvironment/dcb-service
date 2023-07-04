package org.olf.dcb.test;

import java.util.function.Function;

import org.reactivestreams.Publisher;

import reactor.core.publisher.Flux;

public class DataAccess {
	public <T> void deleteAll(Publisher<T> allRecords,
		Function<T, Publisher<Void>> deleteFunction) {

		Flux.from(allRecords)
			.flatMap(deleteFunction)
			.then()
			.block();
	}
}
