package com.musicapp.listener.dto;

import com.musicapp.listener.ListenerAttitude;
import jakarta.validation.constraints.NotNull;

public record AttitudeRequest(@NotNull ListenerAttitude attitude) {
}
