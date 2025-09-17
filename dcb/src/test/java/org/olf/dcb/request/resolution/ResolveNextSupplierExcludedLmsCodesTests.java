package org.olf.dcb.request.resolution;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.sameInstance;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.Mockito.*;

import io.micronaut.context.annotation.Value;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.olf.dcb.core.ConsortiumService;
import org.olf.dcb.core.HostLmsService;
import org.olf.dcb.core.model.FunctionalSetting;
import org.olf.dcb.core.model.PatronRequest;
import org.olf.dcb.request.fulfilment.PatronRequestAuditService;
import org.olf.dcb.request.fulfilment.PatronRequestService;
import org.olf.dcb.request.fulfilment.RequestWorkflowContext;
import org.olf.dcb.request.workflow.ResolveNextSupplierTransition;
import org.olf.dcb.test.DcbTest;
import org.olf.dcb.test.PublisherUtils;
import reactor.core.publisher.Mono;

import io.micronaut.context.BeanProvider;

import java.util.ArrayList;
import java.util.List;

@DcbTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ResolveNextSupplierExcludedLmsCodesTests {

	@Inject
	@Value("${dcb.reresolution.excluded.lms.codes}") // src/test/resources/application-test.yml
	List<String> excludedLmsCodes;

	private ResolveNextSupplierTransition newTransition(List<String> excluded, ConsortiumService consortium) {
		HostLmsService hostLmsService = mock(HostLmsService.class);
		PatronRequestAuditService audit = mock(PatronRequestAuditService.class);
		when(audit.addAuditEntry(any(), any())).thenReturn(Mono.empty());
		PatronRequestResolutionService resolution = mock(PatronRequestResolutionService.class);
		PatronRequestService prs = mock(PatronRequestService.class);
		SupplierRequestService srs = mock(SupplierRequestService.class);

		@SuppressWarnings("unchecked")
		BeanProvider<PatronRequestService> prsProvider = mock(BeanProvider.class);
		when(prsProvider.get()).thenReturn(prs);

		@SuppressWarnings("unchecked")
		BeanProvider<SupplierRequestService> srsProvider = mock(BeanProvider.class);
		when(srsProvider.get()).thenReturn(srs);

		return new ResolveNextSupplierTransition(
			hostLmsService, audit, resolution, prsProvider, srsProvider, consortium, excluded);
	}

	private RequestWorkflowContext ctx(String borrowingLmsCode) {
		RequestWorkflowContext ctx = mock(RequestWorkflowContext.class, RETURNS_DEEP_STUBS);
		when(ctx.getWorkflowMessages()).thenReturn(new ArrayList<>());
		when(ctx.getPatronSystemCode()).thenReturn(borrowingLmsCode);

		PatronRequest pr = mock(PatronRequest.class);
		when(ctx.getPatronRequest()).thenReturn(pr);
		// make cancel path a no-op if hit
		when(pr.getLocalRequestStatus()).thenReturn(org.olf.dcb.core.interaction.HostLmsRequest.HOLD_MISSING);
		when(pr.getActiveWorkflow()).thenReturn("RET-TEST");
		when(ctx.setPatronRequest(any())).thenAnswer(i -> ctx);
		return ctx;
	}

	@Test
	void shortCircuitsWhenBorrowingLmsCodeIsExcluded_byYamlConfig() {
		// sanity: YAML bound as expected
		assertThat(excludedLmsCodes, hasItem("ALMA"));

		ConsortiumService consortium = mock(ConsortiumService.class);
		var transition = newTransition(excludedLmsCodes, consortium);
		var context = ctx("ALMA"); // excluded by YAML

		var returned = PublisherUtils.singleValueFrom(transition.attempt(context));

		assertThat(returned, sameInstance(context));
		assertThat(returned.getWorkflowMessages(), hasItem(startsWith("ReResolution is NOT required")));
		// and the consortium policy wasn't touched
		verifyNoInteractions(consortium);
	}

	@Test
	void fallsThroughWhenCodeNotInYamlConfig() {
		ConsortiumService consortium = mock(ConsortiumService.class);
		FunctionalSetting setting = mock(FunctionalSetting.class);
		when(setting.getEnabled()).thenReturn(false);
		when(consortium.findOneConsortiumFunctionalSetting(any())).thenReturn(Mono.just(setting));

		var transition = newTransition(excludedLmsCodes, consortium);
		var context = ctx("POLARIS"); // not excluded by the YAML above

		var returned = PublisherUtils.singleValueFrom(transition.attempt(context));

		assertThat(returned, sameInstance(context));
		assertThat(returned.getWorkflowMessages(), hasItem(anyOf(
			startsWith("ReResolution is NOT required"), startsWith("ReResolution is required"))));
		verify(consortium, times(1)).findOneConsortiumFunctionalSetting(any());
	}
}
