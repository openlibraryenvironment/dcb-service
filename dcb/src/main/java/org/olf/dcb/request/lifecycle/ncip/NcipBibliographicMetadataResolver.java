package org.olf.dcb.request.lifecycle.ncip;

import jakarta.inject.Singleton;
import java.util.Optional;
import org.olf.dcb.core.model.BibRecord;
import org.olf.dcb.request.fulfilment.RequestWorkflowContext;
import org.olf.dcb.request.resolution.SharedIndexService;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@Singleton
@Slf4j
class NcipBibliographicMetadataResolver {
	private final SharedIndexService sharedIndexService;

	NcipBibliographicMetadataResolver(SharedIndexService sharedIndexService) {
		this.sharedIndexService = sharedIndexService;
	}

	Mono<NcipBibliographicMetadata> resolve(RequestWorkflowContext context) {
		String contextTitle = Optional.ofNullable(context)
			.map(RequestWorkflowContext::getPickupBibTitle)
			.filter(NcipBibliographicMetadataResolver::hasText)
			.orElse(null);
		var bibClusterId = Optional.ofNullable(context)
			.map(RequestWorkflowContext::getPatronRequest)
			.map(org.olf.dcb.core.model.PatronRequest::getBibClusterId)
			.orElse(null);
		if (bibClusterId == null) {
			return Mono.just(new NcipBibliographicMetadata(contextTitle, null, null));
		}

		return sharedIndexService.findSelectedBib(bibClusterId)
			.map(record -> from(record, contextTitle))
			.defaultIfEmpty(new NcipBibliographicMetadata(contextTitle, null, null))
			.onErrorResume(error -> {
				log.warn("Cannot resolve NCIP bibliographic metadata from bib cluster {}", bibClusterId, error);
				return Mono.just(new NcipBibliographicMetadata(contextTitle, null, null));
			});
	}

	private NcipBibliographicMetadata from(BibRecord record, String contextTitle) {
		return new NcipBibliographicMetadata(
			hasText(contextTitle) ? contextTitle : record.getTitle(),
			record.getAuthor() != null ? record.getAuthor().getName() : null,
			record.getEdition());
	}

	private static boolean hasText(String value) {
		return value != null && !value.isBlank();
	}
}
