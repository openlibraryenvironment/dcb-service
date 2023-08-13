package org.olf.dcb.core.api.types;

import java.util.UUID;

import io.micronaut.serde.annotation.Serdeable;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Builder
@Data
@AllArgsConstructor
@Serdeable
public class ClusterBibDTO {
        UUID bibId;
        String title;
        String sourceRecordId;
        String sourceSystem;
        String metadataScore;
        String clusterReason;
}

