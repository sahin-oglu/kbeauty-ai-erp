package com.sahinoglu.kbeautyaierp.ai;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChatRequest(@NotBlank @Size(max = 4000) String message) {
}
