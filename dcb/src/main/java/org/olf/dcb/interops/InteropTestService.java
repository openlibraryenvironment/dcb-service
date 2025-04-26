package org.olf.dcb.interops;

import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.olf.dcb.core.HostLmsService;
import org.olf.dcb.core.interaction.HostLmsClient;

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

	private HostLmsService hostLmsService;

	public InteropTestService(HostLmsService hostLmsService) {
		this.hostLmsService = hostLmsService;
	}

	public Flux<InteropTestResult> testIls(String code) {

		log.debug("testIls {}",code);

		List<Mono<InteropTestResult>> tests = List.of(
			runPatronTests()
		);

		return hostLmsService.getClientFor(code)
			.flatMapMany( hostLms ->
				Flux.concat(tests)
					.contextWrite(ctx -> ctx.put("hostLms", hostLms) ) );

	}

	Mono<InteropTestResult> runPatronTests() {

    return Mono.deferContextual(ctx -> {
        HostLmsClient thing = ctx.get("HostLms");

        // Build and return your TestResult
        return Mono.just(InteropTestResult.builder().build());
    });
	}
}
