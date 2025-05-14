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
import org.olf.dcb.core.interaction.*;

import java.time.Duration;

import org.olf.dcb.core.model.BibRecord;
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

	public Mono<PingResponse> ping(String code) {
		return hostLmsService.getClientFor(code)
			.flatMap( hostLms -> hostLms.ping() );
	}

	public Flux<InteropTestResult> testIls(String code) {

		log.debug("testIls {}",code);


		List<Function<InteropTestContext, Mono<InteropTestResult>>> testSteps = List.of(
    	params -> patronCreateTest(params),
      params -> patronValidateTest(params),
			params -> createBibTest(params),
			params -> createItemTest(params),
			params -> getItemsTest(params),
			params -> deleteItemTest(params),
			params -> deleteBibTest(params),
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

	private Mono<InteropTestResult> getItemsTest(InteropTestContext testCtx) {
		HostLmsClient hostLms = testCtx.getHostLms();
		String bib_id = (String) testCtx.getValues().get("mms_id");

		return Mono.delay(Duration.ofSeconds(10))
			.flatMap(tick -> hostLms.getItems(BibRecord.builder().sourceRecordId(bib_id).build()))
			.map(items -> InteropTestResult.builder()
				.stage("setup")
				.step("live availability")
				.result("OK")
				.note("getItems returned " + items + " from "+hostLms.getHostLmsCode()+ " at "+Instant.now().toString())
				.build()
			);
	}

	private Mono<InteropTestResult> deleteItemTest(InteropTestContext testCtx) {
		HostLmsClient hostLms = testCtx.getHostLms();
		String item_id = (String) testCtx.getValues().get("item_id");
		String mms_id = (String) testCtx.getValues().get("mms_id");
		String holding_id = (String) testCtx.getValues().get("holding_id");

		if ( item_id != null ) {
			return hostLms.deleteItem(item_id, holding_id, mms_id)
				.map(result -> {
					return InteropTestResult.builder()
						.stage("cleanup")
						.step("item")
						.result("OK")
						.note("delete item returned "+result+" from "+hostLms.getHostLmsCode()+ " at "+Instant.now().toString())
						.build();
				});
		}
		else {
			return Mono.just(InteropTestResult.builder()
				.stage("cleanup")
				.step("item")
				.result("NOT-RUN")
				.note("There was no item_id in context. Unable to test item delete for "+hostLms.getHostLmsCode()+ " at "+Instant.now().toString())
				.build());
		}
	}

	private Mono<InteropTestResult> createItemTest(InteropTestContext testCtx) {
		HostLmsClient hostLms = testCtx.getHostLms();
		String mms_id = (String) testCtx.getValues().get("mms_id");
		String barcode = "TEST-" + Instant.now().toString();

		return hostLms.createItem(CreateItemCommand.builder().bibId(mms_id).locationCode("GTLSC").barcode(barcode).build())
			.map(item -> {
					testCtx.getValues().put("item_id", item.getLocalId());
					testCtx.getValues().put("holding_id", item.getHoldingId());
					return InteropTestResult.builder()
						.stage("setup")
						.step("bib")
						.result("OK")
						.note("Created item with ID "+item.getLocalId()+" at "+hostLms.getHostLmsCode()+ " at "+Instant.now().toString())
						.build();
				}
			);
	}

	private Mono<InteropTestResult> deleteBibTest(InteropTestContext testCtx) {
		HostLmsClient hostLms = testCtx.getHostLms();
		String mms_id = (String) testCtx.getValues().get("mms_id");

		if ( mms_id != null ) {
			return hostLms.deleteBib(mms_id)
				.map(result -> {
					return InteropTestResult.builder()
						.stage("cleanup")
						.step("bib")
						.result("OK")
						.note("delete bib returned "+result+" from "+hostLms.getHostLmsCode()+ " at "+Instant.now().toString())
						.build();
				});
		}
		else {
			return Mono.just(InteropTestResult.builder()
				.stage("cleanup")
				.step("bib")
				.result("NOT-RUN")
				.note("There was no mms_id in context. Unable to test bib delete for "+hostLms.getHostLmsCode()+ " at "+Instant.now().toString())
				.build());
		}
	}

	private Mono<InteropTestResult> createBibTest(InteropTestContext testCtx) {
		HostLmsClient hostLms = testCtx.getHostLms();

		return hostLms.createBib(Bib.builder().author("testAuthor").title("testTitle").build())
			.map(bibId -> {
				testCtx.getValues().put("mms_id", bibId);
				return InteropTestResult.builder()
						.stage("setup")
						.step("bib")
						.result("OK")
						.note("Created bib with ID "+bibId+" at "+hostLms.getHostLmsCode()+ " at "+Instant.now().toString())
						.build();
				}
			);
	}

	private Mono<InteropTestResult> patronValidateTest(InteropTestContext testCtx) {
		HostLmsClient hostLms = testCtx.getHostLms();
		String remotePatronId = (String) testCtx.getValues().get("remotePatronId");

		return hostLms.getPatronByIdentifier(remotePatronId)
			.map(patron -> InteropTestResult.builder()
				.stage("setup")
				.step("patron")
				.result("OK")
				.note("Validated patron with remotePatronId "+remotePatronId+" at "+hostLms.getHostLmsCode()+ " at "+Instant.now().toString())
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
