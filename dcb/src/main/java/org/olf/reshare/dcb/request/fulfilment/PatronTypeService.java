package org.olf.reshare.dcb.request.fulfilment;

import io.micronaut.context.annotation.Prototype;
import io.micronaut.context.annotation.Value;

@Prototype
public class PatronTypeService {
	private final Integer fixedPatronType;

	public PatronTypeService(
		@Value("${dcb.requests.supplying.patron-type:210}") Integer fixedPatronType) {

		this.fixedPatronType = fixedPatronType;
	}

	public int determinePatronType() {
		return fixedPatronType;
	}
}
