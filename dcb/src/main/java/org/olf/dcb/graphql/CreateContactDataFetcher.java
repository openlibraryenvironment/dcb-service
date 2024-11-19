package org.olf.dcb.graphql;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.exceptions.HttpStatusException;
import org.olf.dcb.core.model.ConsortiumContact;
import org.olf.dcb.core.model.Person;
import org.olf.dcb.storage.ConsortiumRepository;
import org.olf.dcb.storage.ConsortiumContactRepository;
import org.olf.dcb.storage.PersonRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;
import io.micronaut.data.r2dbc.operations.R2dbcOperations;
import jakarta.inject.Singleton;
import reactor.core.publisher.Mono;
import services.k_int.utils.UUIDUtils;
import static org.olf.dcb.core.Constants.UUIDs.NAMESPACE_DCB;
@Singleton
public class CreateContactDataFetcher implements DataFetcher<CompletableFuture<ConsortiumContact>> {

	private static Logger log = LoggerFactory.getLogger(DataFetchers.class);

	private PersonRepository personRepository;

	private ConsortiumContactRepository consortiumContactRepository;

	private ConsortiumRepository consortiumRepository;


	private R2dbcOperations r2dbcOperations;

	public CreateContactDataFetcher(PersonRepository personRepository, ConsortiumContactRepository consortiumContactRepository, ConsortiumRepository consortiumRepository, R2dbcOperations r2dbcOperations) {
			this.consortiumContactRepository = consortiumContactRepository;
			this.consortiumRepository = consortiumRepository;
			this.personRepository = personRepository;
			this.r2dbcOperations = r2dbcOperations;
		}

		@Override
		public CompletableFuture<ConsortiumContact> get(DataFetchingEnvironment env) {
			Map<String, Object> input_map = env.getArgument("input");
			Optional<String> reason = Optional.ofNullable(input_map.get("reason"))
				.map(Object::toString);
			Optional<String> changeReferenceUrl = Optional.ofNullable(input_map.get("changeReferenceUrl"))
				.map(Object::toString);
			Optional<String> changeCategory = Optional.ofNullable(input_map.get("changeCategory"))
				.map(Object::toString);
			String userString = Optional.ofNullable(env.getGraphQlContext().get("userName"))
				.map(Object::toString)
				.orElse("User not detected");
			UUID consortiumId = input_map.get("consortiumId") != null ? UUID.fromString(input_map.get("consortiumId").toString()) : null;
			Collection<String> roles = env.getGraphQlContext().get("roles");
			log.debug("createContactDataFetcher input: {}, consortiumId: {}", input_map, consortiumId);
			if (roles == null || (!roles.contains("ADMIN") && !roles.contains("CONSORTIUM_ADMIN"))) {
				log.warn("createContactDataFetcher: Access denied for user {}: user does not have the required role to update a consortium contact.", userString);
				throw new HttpStatusException(HttpStatus.UNAUTHORIZED, "Access denied: you do not have the required role to perform this action.");
			}
			Person newPerson = Person.builder()
				.id(UUIDUtils.nameUUIDFromNamespaceAndString(NAMESPACE_DCB, "Person:" + input_map.get("firstName") + input_map.get("lastName") + input_map.get("role") + input_map.get("email")))
				.firstName(input_map.get("firstName").toString())
				.lastName(input_map.get("lastName").toString())
				.role(input_map.get("role").toString())
				.email(input_map.get("email").toString())
				.isPrimaryContact(Boolean.parseBoolean(input_map.get("isPrimaryContact").toString()))
				.lastEditedBy(userString).build();
			changeReferenceUrl.ifPresent(newPerson::setChangeReferenceUrl);
			changeCategory.ifPresent(newPerson::setChangeCategory);
			reason.ifPresent(newPerson::setReason);

			ConsortiumContact contact = ConsortiumContact.builder().build();
			return Mono.from(r2dbcOperations.withTransaction(status ->
				// First save the person
				Mono.from(personRepository.save(newPerson))
					.flatMap(savedPerson -> {
						// Find the consortium and create the contact
						return Mono.from(consortiumRepository.findById(consortiumId))
							.switchIfEmpty(Mono.error(new HttpStatusException(HttpStatus.NOT_FOUND,
								"Consortium with consortiumId " + consortiumId + " not found")))
							.flatMap(consortium -> {
								contact.setConsortium(consortium);
								contact.setPerson(savedPerson);
								contact.setId(UUIDUtils.nameUUIDFromNamespaceAndString(NAMESPACE_DCB, "ConsortiumContact:" + contact.getConsortium().getId() + ","+contact.getPerson().getId()));
								return Mono.from(consortiumContactRepository.saveOrUpdate(contact));
							});
					})
			)).toFuture();
		}
}
