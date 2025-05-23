package org.olf.dcb.operations;

import org.reactivestreams.Publisher;

import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Singleton
@Slf4j
public class OperationsService {
	private final OfficeHours officeHours;
	
	public OperationsService(OfficeHours officeHours) {
		this.officeHours = officeHours;
	}
	
	public OfficeHours getOfficeHours() {
		return officeHours;
	}
	
	public <T> Publisher<T> subscribeOnlyOutsideOfficeHours( Publisher<T> publisher ) {
		if (getOfficeHours().isOutsideHours()) {
			return Flux.from( publisher )
				.doOnSubscribe( _sub -> {
					log.trace("Ouside office hours... run.");
				});
		}
		
		
		return Mono.<T>empty()
			.doOnSubscribe( _sub -> {
				log.trace("Returning empty publisher as currently inside office hours.");
			});
	}
}
