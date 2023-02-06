package org.olf.reshare.dcb.core.model;

import java.util.UUID;

import javax.validation.constraints.NotNull;

import io.micronaut.core.annotation.NonNull;

public interface Agency {
    
    @NonNull
    @NotNull
    public UUID getId();

    @NonNull
    @NotNull
    public String getName();
    
    @NonNull
    @NotNull
    public HostLms getHostLms();
}
