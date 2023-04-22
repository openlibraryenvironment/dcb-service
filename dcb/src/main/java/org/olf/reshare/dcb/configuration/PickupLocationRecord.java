package org.olf.reshare.dcb.configuration;


import io.micronaut.core.annotation.Creator;
import io.micronaut.serde.annotation.Serdeable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.olf.reshare.dcb.core.model.HostLms;
import services.k_int.tests.ExcludeFromGeneratedCoverageReport;

import java.util.UUID;

@Builder
@Data
@NoArgsConstructor(onConstructor_ = @Creator())
@AllArgsConstructor
@Serdeable
@ExcludeFromGeneratedCoverageReport
public class PickupLocationRecord implements ConfigurationRecord {

    public static final String RECORD_TYPE = "PICKUP_LOCATION";

    private UUID id;

    private String code;

    private String name;

    private HostLms lms;

    public String getRecordType() {
        return RECORD_TYPE;
    }

}