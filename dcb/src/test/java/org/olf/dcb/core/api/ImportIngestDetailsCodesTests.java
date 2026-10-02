package org.olf.dcb.core.api;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collection;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentMatchers;
import org.olf.dcb.core.HostLmsService;
import org.olf.dcb.storage.HostLmsRepository;

import io.micronaut.context.event.ApplicationEventPublisher;
import reactor.core.publisher.Mono;

// A unit test, not an API test: the all-Host LMS report is cached for the life of the context,
// so an API test that reached it would hand its count to every other test in the class
class ImportIngestDetailsCodesTests {
	private HostLmsService hostLmsService;
	private HostLmssController controller;

	@BeforeEach
	@SuppressWarnings("unchecked")
	void beforeEach() {
		hostLmsService = mock(HostLmsService.class);
		when(hostLmsService.getAllImportIngestDetails()).thenReturn(Mono.just(List.of()));
		when(hostLmsService.getImportIngestDetails(ArgumentMatchers.<Collection<String>>any()))
			.thenReturn(Mono.just(List.of()));

		controller = new HostLmssController(mock(HostLmsRepository.class), hostLmsService,
			mock(ApplicationEventPublisher.class));
	}

	@ParameterizedTest
	@NullSource
	@ValueSource(strings = { "", "   ", ",", " , ,", ",,," })
	void shouldReportEveryHostLmsWhenTheCodesNameNone(String hostLmsCodes) {
		controller.getAllImportIngestDetails(hostLmsCodes).block();

		verify(hostLmsService).getAllImportIngestDetails();
		verify(hostLmsService, never()).getImportIngestDetails(ArgumentMatchers.<Collection<String>>any());
	}

	@ParameterizedTest
	@ValueSource(strings = { "first, second,first", " ,first,,second, " })
	void shouldCountOnlyTheNamedCodesOnceEach(String hostLmsCodes) {
		controller.getAllImportIngestDetails(hostLmsCodes).block();

		verify(hostLmsService).getImportIngestDetails(List.of("first", "second"));
		verify(hostLmsService, never()).getAllImportIngestDetails();
	}
}
