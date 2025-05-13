package org.olf.dcb.interops;

import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.time.Instant;

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

import org.olf.dcb.core.interaction.Patron;
import org.olf.dcb.core.interaction.PingResponse;

@Slf4j
@Singleton
public class InteropTestService {

	private HostLmsService hostLmsService;

	public InteropTestService(HostLmsService hostLmsService) {
		this.hostLmsService = hostLmsService;
	}

	public Mono<PingResponse> ping(String code) {
		return hostLmsService.getClientFor(code)
			.flatMap( hostLms -> hostLms.ping() );
	}

	public Flux<InteropTestResult> testIls(String code) {

		log.debug("testIls {}",code);


		List<Function<InteropTestContext, Mono<InteropTestResult>>> testSteps = List.of(
    	params -> patronCreateTest(params),
      params -> patronValidateTest(params),
			params -> patronDeleteTest(params)
    );

		return hostLmsService.getClientFor(code)
			.map ( hostLms -> {
				InteropTestContext testCtx = InteropTestContext.builder()
					.hostLms(hostLms)
					.values(new HashMap<String,Object>())
					.build();
				return testCtx;
			})
			.flatMapMany( testCtx ->
				Flux.fromIterable(testSteps)
					.concatMap(test -> test.apply(testCtx) )
			);

	}

	private Mono<InteropTestResult> patronValidateTest(InteropTestContext testCtx) {
		HostLmsClient hostLms = testCtx.getHostLms();
		String testPatronId = (String) testCtx.getValues().get("testPatronId");

		return hostLms.getPatronByIdentifier(testPatronId)
			.map(patron -> InteropTestResult.builder()
				.stage("setup")
				.step("patron")
				.result("OK")
				.note("Validated patron with testPatronId "+testPatronId+" at "+hostLms.getHostLmsCode()+ " at "+Instant.now().toString())
				.build()
			);
	}

	Mono<InteropTestResult> patronCreateTest(InteropTestContext testCtx) {

		HostLmsClient hostLms = testCtx.getHostLms();

		String temporaryPatronId = UUID.randomUUID().toString();
		testCtx.getValues().put("testPatronId", temporaryPatronId);

		log.info("Patron tests {}",testCtx.getValues());

		// Build and return your TestResult
		return hostLms.createPatron(
			Patron.builder()
				.localNames( List.of( temporaryPatronId+"-fn", temporaryPatronId+".sn" ) )
				.localBarcodes( List.of( temporaryPatronId) )
				.uniqueIds( List.of( temporaryPatronId) )
				// .localPatronType( "" )
				.canonicalPatronType( "CIRC" )
				.build()
			)
			.map(newPatronId -> {
				testCtx.getValues().put("remotePatronId", newPatronId);
				return InteropTestResult.builder()
					.stage("setup")
					.step("patron")
					.result("OK")
					.note("Created patron with ID "+newPatronId+" at "+hostLms.getHostLmsCode()+ " at "+Instant.now().toString())
					.build();
			});
	}

	Mono<InteropTestResult> patronDeleteTest(InteropTestContext testCtx) {

		HostLmsClient hostLms = testCtx.getHostLms();
		String remotePatronId = (String) testCtx.getValues().get("remotePatronId");

		if ( remotePatronId != null ) {
			return hostLms.deletePatron(remotePatronId)
				.map(result -> {
	        return InteropTestResult.builder()
  	        .stage("cleanup")
    	      .step("patron")
      	    .result("OK")
        	  .note("deletePatron returned "+result+" from "+hostLms.getHostLmsCode()+ " at "+Instant.now().toString())
          	.build();
				});
		}
		else {
			return Mono.just(InteropTestResult.builder()
				.stage("cleanup")
				.step("patron")
				.result("NOT-RUN")
				.note("There was no patron-id in context. Unable to test patron delete for "+hostLms.getHostLmsCode()+ " at "+Instant.now().toString())
				.build());
		}
	}
}
