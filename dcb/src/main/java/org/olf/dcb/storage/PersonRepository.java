package org.olf.dcb.storage;

import java.util.Collection;
import java.util.UUID;

import org.olf.dcb.core.model.Person;
import org.reactivestreams.Publisher;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.async.annotation.SingleResult;
import io.micronaut.data.annotation.Query;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import reactor.core.publisher.Mono;

public interface PersonRepository {
	@NonNull
	@SingleResult
	Publisher<? extends Person> save(@Valid @NotNull @NonNull Person person);

	@NonNull
	@SingleResult
	Publisher<? extends Person> persist(@Valid @NotNull @NonNull Person person);

	@NonNull
	@SingleResult
	Publisher<? extends Person> update(@Valid @NotNull @NonNull Person person);

	@NonNull
	@SingleResult
	Publisher<? extends Person> findById(@NonNull UUID id);

	@NonNull
	@SingleResult
	Publisher<Boolean> existsById(@NonNull UUID id);

	@NonNull
	@SingleResult
	Publisher<Page<Person>> queryAll(Pageable page);

	@NonNull
	Publisher<Person> queryAll();

	@Query(value = "SELECT * from person where id in (:ids)", nativeQuery = true)
	Publisher<Person> findByIds(@NonNull Collection<UUID> ids);

	Publisher<Void> delete(UUID id);

	@SingleResult
	@NonNull
	default Publisher<Person> saveOrUpdate(@Valid @NotNull Person person) {
		return Mono.from(this.existsById(person.getId()))
			.flatMap( update -> Mono.from( update ? this.update(person) : this.save(person)) )
			;
	}

}
