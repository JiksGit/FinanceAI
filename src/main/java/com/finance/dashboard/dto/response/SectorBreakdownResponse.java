package com.finance.dashboard.dto.response;

import java.util.List;

public record SectorBreakdownResponse(
        List<SectorItem> sectors
) {
    public record SectorItem(
            String sector,
            int stockCount,
            double percentage
    ) {}
}
