package com.sahinoglu.kbeautyaierp.ai;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonAlias;

public enum UiDataSource {
    @JsonProperty("brand_sales") @JsonAlias("BRAND_SALES") BRAND_SALES,
    @JsonProperty("low_stock") @JsonAlias("LOW_STOCK") LOW_STOCK,
    @JsonProperty("product_stock") @JsonAlias("PRODUCT_STOCK") PRODUCT_STOCK,
    @JsonProperty("customer_orders") @JsonAlias("CUSTOMER_ORDERS") CUSTOMER_ORDERS,
    @JsonProperty("sales_order") @JsonAlias("SALES_ORDER") SALES_ORDER,
    @JsonProperty("top_selling") @JsonAlias("TOP_SELLING") TOP_SELLING,
    @JsonProperty("unsupported") @JsonAlias("UNSUPPORTED") UNSUPPORTED
}
