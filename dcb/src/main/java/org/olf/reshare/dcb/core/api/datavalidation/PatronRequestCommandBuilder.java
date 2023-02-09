package org.olf.reshare.dcb.core.api.datavalidation;

import java.util.UUID;

public class PatronRequestCommandBuilder {
    private UUID id;
    private CitationCommand citation;
    private RequestorCommand requestor;
    private PickupLocationCommand pickupLocation;

    public PatronRequestCommandBuilder setId(UUID id) {
        this.id = id;
        return this;
    }

    public PatronRequestCommandBuilder setCitation(CitationCommand citation) {
        this.citation = citation;
        return this;
    }

    public PatronRequestCommandBuilder setRequestor(RequestorCommand requestor) {
        this.requestor = requestor;
        return this;
    }

    public PatronRequestCommandBuilder setPickupLocation(PickupLocationCommand pickupLocation) {
        this.pickupLocation = pickupLocation;
        return this;
    }

    public PatronRequestCommand createPatronRequestCommand() {
        return new PatronRequestCommand(id, citation, requestor, pickupLocation);
    }
}
