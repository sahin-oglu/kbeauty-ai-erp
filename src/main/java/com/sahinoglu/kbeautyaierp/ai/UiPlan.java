package com.sahinoglu.kbeautyaierp.ai;

import org.jspecify.annotations.Nullable;

public record UiPlan(UiType type, UiDataSource source, @Nullable Long id, @Nullable String brand) {
    public UiPlan(UiType type, UiDataSource source, Long id) {
        this(type, source, id, null);
    }
}
