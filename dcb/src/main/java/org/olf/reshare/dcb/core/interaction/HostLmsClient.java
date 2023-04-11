package org.olf.reshare.dcb.core.interaction;

import java.util.List;
import java.util.Map;

import org.olf.reshare.dcb.core.model.HostLms;
import org.olf.reshare.dcb.core.model.Item;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface HostLmsClient {
	HostLms getHostLms();

	Flux<Map<String, ?>> getAllBibData();

	Mono<List<Item>> getItemsByBibId(String bibId, String hostLmsCode);
}
