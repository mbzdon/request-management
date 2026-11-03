package pl.bzdon.requestmanagement.endpoint.dto;

import pl.bzdon.requestmanagement.domain.model.HistoryEntry;
import pl.bzdon.requestmanagement.domain.model.statemachine.RequestAction;
import pl.bzdon.requestmanagement.domain.model.statemachine.RequestState;

import java.time.Instant;

public record HistoryEntryDto(RequestState from, RequestState to,
                              RequestAction action, String reason,
                              Instant occurredAt) {

    public static HistoryEntryDto from(HistoryEntry entry) {
        return new HistoryEntryDto(entry.from(), entry.to(), entry.action(), entry.reason(), entry.occurredAt());
    }
}
