package pl.bzdon.requestmanagement.domain.model;

import pl.bzdon.requestmanagement.domain.model.statemachine.RequestAction;
import pl.bzdon.requestmanagement.domain.model.statemachine.RequestState;

import java.time.Instant;

public record HistoryEntry(RequestState from, RequestState to, RequestAction action, String reason,
                           Instant occurredAt) {
}
