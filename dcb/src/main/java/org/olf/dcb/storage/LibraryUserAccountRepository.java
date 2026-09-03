package org.olf.dcb.storage;

import java.util.UUID;

import org.olf.dcb.core.model.LibraryUserAccount;
import org.reactivestreams.Publisher;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.async.annotation.SingleResult;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

/**
 * Accounts are read one library at a time and never consortium-wide.
 *
 * There is deliberately no findAll over every library: a listing bounded by one library's
 * staff is tens of rows, and a consortium-wide roster across 500 libraries is a different
 * query with different scale properties that nothing has asked for. Adding one later means
 * keyset pagination, not this interface plus a large page size.
 */
public interface LibraryUserAccountRepository {

	@NonNull
	@SingleResult
	Publisher<? extends LibraryUserAccount> save(@Valid @NotNull @NonNull LibraryUserAccount account);

	@NonNull
	@SingleResult
	Publisher<? extends LibraryUserAccount> update(@Valid @NotNull @NonNull LibraryUserAccount account);

	@NonNull
	@SingleResult
	Publisher<LibraryUserAccount> findById(@NonNull UUID id);

	/** One library's accounts. Bounded by staff headcount - tens, not thousands. */
	@NonNull
	Publisher<LibraryUserAccount> findByLibraryIdOrderByEmail(@NonNull UUID libraryId);

	/** Used to refuse a second account for an address that already has one here. */
	@NonNull
	@SingleResult
	Publisher<LibraryUserAccount> findByLibraryIdAndEmail(@NonNull UUID libraryId, @NonNull String email);

	Publisher<Void> delete(UUID id);
}
