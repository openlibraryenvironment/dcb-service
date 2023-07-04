package org.olf.dcb.configuration;

import java.util.UUID;

import org.olf.dcb.core.model.HostLms;

import io.micronaut.core.annotation.Creator;
import io.micronaut.serde.annotation.Serdeable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import services.k_int.tests.ExcludeFromGeneratedCoverageReport;

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
