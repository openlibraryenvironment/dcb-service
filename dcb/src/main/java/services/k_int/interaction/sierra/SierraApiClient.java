package services.k_int.interaction.sierra;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.async.annotation.SingleResult;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Delete;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.http.annotation.Put;
import services.k_int.interaction.auth.BasicAuthClient;
import services.k_int.interaction.sierra.bibs.BibParams;
import services.k_int.interaction.sierra.bibs.BibParams.BibParamsBuilder;
import services.k_int.interaction.sierra.bibs.BibPatch;
import services.k_int.interaction.sierra.bibs.BibResultSet;
import services.k_int.interaction.sierra.configuration.BranchResultSet;
import services.k_int.interaction.sierra.configuration.PatronMetadata;
import services.k_int.interaction.sierra.configuration.PickupLocationInfo;
import services.k_int.interaction.sierra.holds.SierraPatronHold;
import services.k_int.interaction.sierra.holds.SierraPatronHoldResultSet;
import services.k_int.interaction.sierra.items.Params;
import services.k_int.interaction.sierra.items.ResultSet;
import services.k_int.interaction.sierra.items.SierraItem;
import services.k_int.interaction.sierra.patrons.InternalPatronValidation;
import services.k_int.interaction.sierra.patrons.ItemPatch;
import services.k_int.interaction.sierra.patrons.PatronHoldPost;
import services.k_int.interaction.sierra.patrons.PatronPatch;
import services.k_int.interaction.sierra.patrons.SierraPatronRecord;

public interface SierraApiClient extends BasicAuthClient {
	String CONFIG_ROOT = "sierra.client";
	Logger log = LoggerFactory.getLogger(SierraApiClient.class);

	private static <T> Collection<T> nullIfEmpty(Collection<T> collection) {
		if (collection == null || collection.size() < 1)
			return null;
		return collection;
	}

	@SingleResult
	default Publisher<BibResultSet> bibs(BibParams params) {
		return bibs(params.getLimit(), params.getOffset(), Objects.toString(params.getCreatedDate(), null),
				Objects.toString(params.getUpdatedDate(), null), nullIfEmpty(params.getFields()), params.getDeleted(),
				Objects.toString(params.getDeletedDate(), null), params.getSuppressed(), nullIfEmpty(params.getLocations()));
	}

	@SingleResult
	default Publisher<BibResultSet> bibs(Consumer<BibParamsBuilder> consumer) {
		return bibs(BibParams.build(consumer));
	}

	@SingleResult
	Publisher<BibResultSet> bibs(@Nullable final Integer limit, @Nullable final Integer offset,
			@Nullable final String createdDate, @Nullable final String updatedDate, @Nullable final Iterable<String> fields,
			@Nullable final Boolean deleted, @Nullable final String deletedDate, @Nullable final Boolean suppressed,
			@Nullable final Iterable<String> locations);

	@SingleResult
	default Publisher<ResultSet> items(Params params) {
		return items(params.getLimit(), params.getOffset(), nullIfEmpty(params.getId()), nullIfEmpty(params.getFields()),
				Objects.toString(params.getCreatedDate(), null), Objects.toString(params.getUpdatedDate(), null),
				Objects.toString(params.getDeletedDate(), null), params.getDeleted(), nullIfEmpty(params.getBibIds()),
				params.getStatus(), Objects.toString(params.getDuedate(), null), params.getSuppressed(),
				nullIfEmpty(params.getLocations()));
	}

	@SingleResult
	default Publisher<ResultSet> items(Consumer<Params.ParamsBuilder> consumer) {
		return items(Params.build(consumer));
	}

	@SingleResult
	Publisher<ResultSet> items(@Nullable final Integer limit, @Nullable final Integer offset,
			@Nullable final Iterable<String> id, @Nullable final Iterable<String> fields, @Nullable final String createdDate,
			@Nullable final String updatedDate, @Nullable final String deletedDate, @Nullable final Boolean deleted,
			@Nullable final Iterable<String> bibIds, @Nullable final String status, @Nullable final String duedate,
			@Nullable final Boolean suppressed, @Nullable final Iterable<String> locations);

	@SingleResult
	Publisher<LinkResult> createItem(final ItemPatch itemPatch);

	@SingleResult
	Publisher<Void> updateItem(final String itemId, final ItemPatch itemPatch);

	@SingleResult
	Publisher<SierraItem> getItem(final String itemId);

	@SingleResult
	Publisher<LinkResult> patrons(final PatronPatch patronPatch);

	@SingleResult
	Publisher<LinkResult> bibs(final BibPatch bibPatch);

	@SingleResult
	Publisher<Void> updateBib(String bibId, BibPatch body);

	@SingleResult
	Publisher<SierraPatronRecord> patronFind(@Nullable final String varFieldTag, @Nullable final String varFieldContent);

	@SingleResult
	Publisher<String> validatePatronCredentials(final InternalPatronValidation body);

	@SingleResult
	Publisher<BranchResultSet> branches(@Nullable Integer limit, @Nullable final Integer offset,
			@Nullable final Iterable<String> fields);

	@SingleResult
	Publisher<List<PickupLocationInfo>> pickupLocations();

	@SingleResult
	Publisher<List<PatronMetadata>> patronMetadata();

	@SingleResult
	Publisher<Void> placeHoldRequest(@Nullable final String id, final PatronHoldPost patronHoldPost);

	@SingleResult
	Publisher<SierraPatronHoldResultSet> patronHolds(@Nullable final String id);

	@SingleResult
	Publisher<SierraPatronHoldResultSet> getAllPatronHolds(@Nullable final Integer limit, @Nullable final Integer offset);

	@SingleResult
	Publisher<SierraPatronRecord> getPatron(@Nullable final Long patronId);

	@SingleResult
	@Put("/patrons/{id}")
	Publisher<SierraPatronRecord> updatePatron(@Nullable @PathVariable("id") final Long patronId,
			@Body final PatronPatch patronPatch);

	@SingleResult
	Publisher<SierraPatronHold> getHold(@Nullable final Long holdId);

	@SingleResult
	Publisher<LinkResult> checkOutItemToPatron(String itemBarcode, String patronBarcode);

	@Delete("/items/{id}")
	Publisher<HttpStatus> deleteItem(String id);

	@Delete("/bibs/{id}")
	Publisher<HttpStatus> deleteBib(String id);

}
