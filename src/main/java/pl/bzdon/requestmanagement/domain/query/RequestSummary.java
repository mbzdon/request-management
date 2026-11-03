package pl.bzdon.requestmanagement.domain.query;

import pl.bzdon.requestmanagement.domain.model.statemachine.RequestState;

import java.time.Instant;
import java.util.UUID;

public record RequestSummary(
    UUID id,
    String name,
    RequestState state,
    Long publicationNumber,
    Instant createdAt,
    Instant updatedAt) {
}
