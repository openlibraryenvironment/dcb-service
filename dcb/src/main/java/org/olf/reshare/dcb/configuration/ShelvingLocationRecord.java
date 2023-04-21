package org.olf.reshare.dcb.configuration;


import io.micronaut.core.annotation.Creator;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import services.k_int.tests.ExcludeFromGeneratedCoverageReport;
import io.micronaut.serde.annotation.Serdeable;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;

import java.util.UUID;

@Builder
@Data
@NoArgsConstructor(onConstructor_ = @Creator())
@AllArgsConstructor
@Serdeable
@ExcludeFromGeneratedCoverageReport
public class ShelvingLocationRecord implements ConfigurationRecord {

				public static final String RECORD_TYPE = "SHELVING_LOCATION";

				private UUID id;

        private String code;
        
        private String name;

        public String getRecordType() {
                return RECORD_TYPE;
        }

}
