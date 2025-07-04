package org.olf.dcb.request.resolution;
import java.util.Comparator;
import org.olf.dcb.core.model.ItemStatusCode;

public class SupplyCandidateItemComparator implements Comparator<SupplyCandidateItem> {

    @Override
    public int compare(SupplyCandidateItem o1, SupplyCandidateItem o2) {
        // 1. AVAILABLE items come first
        int statusComparison = Boolean.compare(
            o2.getItem().getStatus().getCode() == ItemStatusCode.AVAILABLE,
            o1.getItem().getStatus().getCode() == ItemStatusCode.AVAILABLE
        );
        if (statusComparison != 0) return statusComparison;

        // 2. Sort by geoDistance (ascending)
        int distanceComparison = Double.compare(o1.getDistance(), o2.getDistance());
        if (distanceComparison != 0) return distanceComparison;

        return 0;
    }
}
