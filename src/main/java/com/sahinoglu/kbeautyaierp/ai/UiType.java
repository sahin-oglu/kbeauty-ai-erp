package com.sahinoglu.kbeautyaierp.ai;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonAlias;

public enum UiType {
    @JsonProperty("table") @JsonAlias("TABLE") TABLE,
    @JsonProperty("metric") @JsonAlias("METRIC") METRIC,
    @JsonProperty("chart") @JsonAlias("CHART") CHART,
    @JsonProperty("form") @JsonAlias("FORM") FORM
}
