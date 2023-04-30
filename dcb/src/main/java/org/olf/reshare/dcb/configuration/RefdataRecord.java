package org.olf.reshare.dcb.configuration;

import io.micronaut.core.annotation.Creator;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.olf.reshare.dcb.core.model.HostLms;
import services.k_int.tests.ExcludeFromGeneratedCoverageReport;
import io.micronaut.serde.annotation.Serdeable;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import java.util.List;
import java.util.UUID;

@Builder
@Data
@NoArgsConstructor(onConstructor_ = @Creator())
@AllArgsConstructor
@Serdeable
@ExcludeFromGeneratedCoverageReport
public class RefdataRecord implements ConfigurationRecord {

    public static final String RECORD_TYPE="REFDATA";

    private UUID id;

    // The host LMS system which managed this shelving location
    private HostLms lms;

    // In essence a code for the LMS holding this value
    private String context;

    // What is the domain of this refata - e.g. patronType
    private String category;

    private String value;

    private String label;

    public String getRecordType() {
        return RECORD_TYPE;
    }

}
