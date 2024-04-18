package org.olf.dcb.storage;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.async.annotation.SingleResult;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.olf.dcb.core.model.Library;
import org.olf.dcb.core.model.Person;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Mono;

import java.util.UUID;

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

	Publisher<Void> delete(UUID id);

	@SingleResult
	@NonNull
	default Publisher<Person> saveOrUpdate(@Valid @NotNull Person person) {
		return Mono.from(this.existsById(person.getId()))
			.flatMap( update -> Mono.from( update ? this.update(person) : this.save(person)) )
			;
	}

}
