//package org.olf.reshare.dcb.storage;
//
//import java.util.UUID;
//
//import javax.validation.Valid;
//import javax.validation.constraints.NotNull;
//
//import org.olf.reshare.dcb.core.model.AgencyDataImpl;
//import org.reactivestreams.Publisher;
//
//import io.micronaut.core.annotation.NonNull;
//import io.micronaut.core.async.annotation.SingleResult;
//import io.micronaut.data.model.Page;
//import io.micronaut.data.model.Pageable;
//
//public interface AgencyRepository {
//
//  @NonNull
//  @SingleResult
//  Publisher<? extends AgencyDataImpl> save(@Valid @NotNull @NonNull AgencyDataImpl agency);
//  
//  @NonNull
//  @SingleResult
//  Publisher<? extends AgencyDataImpl> update(@Valid @NotNull @NonNull AgencyDataImpl agency);
//
//  @NonNull
//  @SingleResult
//  Publisher<AgencyDataImpl> findById(@NonNull UUID id);
//
//  @NonNull
//  Publisher<AgencyDataImpl> findAll();
//  
//  @NonNull
//  @SingleResult
//  Publisher<Page<AgencyDataImpl>> findAll(Pageable page);
//  
//  @NonNull
//  @SingleResult
//  Publisher<Boolean> existsById(@NonNull UUID id);
//
//  public default void cleanUp() {
//  };
//
//  public default void commit() {
//  }
//}
