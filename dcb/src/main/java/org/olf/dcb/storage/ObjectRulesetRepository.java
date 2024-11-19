package org.olf.dcb.storage;

import java.util.Collection;
import java.util.UUID;

import org.olf.dcb.rules.ObjectRuleset;
import org.reactivestreams.Publisher;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.async.annotation.SingleResult;
import io.micronaut.data.annotation.Query;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import reactor.core.publisher.Mono;

public interface ObjectRulesetRepository {

	@NonNull
	@SingleResult
	Publisher<? extends ObjectRuleset> save(@Valid @NotNull @NonNull ObjectRuleset patronIdentity);

	@NonNull
	Publisher<Page<ObjectRuleset>> queryAll(Pageable page);
	

	@NonNull
	Publisher<ObjectRuleset> findAll();

	@NonNull
	Publisher<Void> delete(UUID id);

	@NonNull
	@SingleResult
	Publisher<? extends ObjectRuleset> update(@Valid @NotNull @NonNull ObjectRuleset patronIdentity);

	@Query(value = "SELECT * from object_ruleset where name in (:names) order by name", nativeQuery = true)
	Publisher<ObjectRuleset> findByNames(@NonNull Collection<String> names);

	@NonNull
	@SingleResult
	Publisher<ObjectRuleset> findByName(@NonNull @NotEmpty String name);

	@NonNull
	@SingleResult
	Publisher<Boolean> existsById(@NonNull String id);

	@SingleResult
	@NonNull
	default Publisher<ObjectRuleset> saveOrUpdate(@Valid @NotNull ObjectRuleset pi) {
		return Mono.from(this.existsById(pi.getName()))
				.flatMap(update -> Mono.from(update ? this.update(pi) : this.save(pi)));
	}
}
