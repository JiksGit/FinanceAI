package com.finance.dashboard.dto.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record EximRateItem(
        @JsonProperty("result") int result,
        @JsonProperty("cur_unit") String curUnit,
        @JsonProperty("cur_nm") String curNm,
        @JsonProperty("deal_bas_r") String dealBasR
) {
}
