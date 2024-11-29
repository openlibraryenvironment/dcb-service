package org.olf.dcb.storage;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.async.annotation.SingleResult;
import io.micronaut.data.annotation.Query;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.olf.dcb.core.model.Role;
import org.olf.dcb.core.model.RoleName;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Mono;

import java.util.Collection;
import java.util.UUID;

public interface RoleRepository {

	@NonNull
	@SingleResult
	Publisher<? extends Role> save(@Valid @NotNull @NonNull Role role);

	@NonNull
	@SingleResult
	Publisher<? extends Role> persist(@Valid @NotNull @NonNull Role role);

	@NonNull
	@SingleResult
	Publisher<? extends Role> update(@Valid @NotNull @NonNull Role role);

	@NonNull
	@SingleResult
	Publisher<? extends Role> findById(@NonNull UUID id);

	@NonNull
	@SingleResult
	Publisher<? extends Role> findByName(@NonNull RoleName name);

	@NonNull
	@SingleResult
	Publisher<Boolean> existsById(@NonNull UUID id);

	@NonNull
	@SingleResult
	Publisher<Page<Role>> queryAll(Pageable page);

	@NonNull
	Publisher<Role> queryAll();

	@Query(value = "SELECT * from role where id in (:ids)", nativeQuery = true)
	Publisher<Role> findByIds(@NonNull Collection<UUID> ids);

	Publisher<Void> delete(UUID id);

	@SingleResult
	@NonNull
	default Publisher<Role> saveOrUpdate(@Valid @NotNull Role role) {
		return Mono.from(this.existsById(role.getId()))
			.flatMap( update -> Mono.from( update ? this.update(role) : this.save(role)) )
			;
	}
}
