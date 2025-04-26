package org.olf.dcb.interops;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import java.time.Duration;

import org.reactivestreams.Publisher;

import io.micronaut.context.BeanContext;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.data.model.Pageable;
import io.micronaut.transaction.TransactionDefinition.Propagation;
import io.micronaut.transaction.annotation.Transactional;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Slf4j
@Singleton
public class InteropTestService {

	public Flux<InteropTestResult> testIls(String code) {

		log.debug("testIls {}",code);

		List<InteropTestResult> result = List.of(InteropTestResult.builder().build());

		return Flux.fromIterable(result)
			.delayElements(Duration.ofSeconds(2));
	}
}
