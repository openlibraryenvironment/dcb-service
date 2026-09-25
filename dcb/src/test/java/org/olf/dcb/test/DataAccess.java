package org.olf.dcb.test;

import java.util.function.Function;

import org.reactivestreams.Publisher;

import reactor.core.publisher.Flux;

public class DataAccess {
	// queryAll retains one test R2DBC connection while it streams rows. Keep the
	// delete on the second connection and do not create a queued delete fan-out.
	private static final int DELETE_CONCURRENCY = 1;

	public <T> void deleteAll(Publisher<T> allRecords,
		Function<T, Publisher<Void>> deleteFunction) {

		Flux.from(allRecords)
			.flatMap(deleteFunction, DELETE_CONCURRENCY)
			.then()
			.block();
	}
}
