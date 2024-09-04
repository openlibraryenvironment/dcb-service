/**
 * Package contents are disabled unless beta environment is present and
 * ingest-v2 feature gate is opened. 
 */

@Beta
@Configuration
@FeatureGate( "ingest-v2" )
@Requires( notEnv = Environment.TEST )
package org.olf.dcb.ingest.job;

import io.micronaut.context.annotation.Requires;
import io.micronaut.context.env.Environment;
import services.k_int.features.FeatureGate;
import services.k_int.features.StabilityLevel.Beta;
import io.micronaut.context.annotation.Configuration;