package pl.bzdon.requestmanagement.endpoint.dto;

import jakarta.validation.constraints.NotNull;
import pl.bzdon.requestmanagement.domain.model.statemachine.RequestAction;

public record TransitionDto(@NotNull RequestAction action, String reason) {
}
