package org.olf.reshare.dcb.core.interaction;

import java.util.Map;

import org.olf.reshare.dcb.core.model.HostLms;

import reactor.core.publisher.Flux;

public interface HostLmsClient {
	
	HostLms getHostLms();
	Flux<Map<String, ?>> getAllBibData();
}
