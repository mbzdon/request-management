package pl.bzdon.requestmanagement.endpoint.dto;

import pl.bzdon.requestmanagement.domain.model.statemachine.RequestState;
import pl.bzdon.requestmanagement.domain.query.RequestSummary;

import java.time.Instant;
import java.util.UUID;

public record RequestSummaryDto(UUID id, String name,
                                RequestState state, Long publicationNumber,
                                Instant createdAt,
                                Instant updatedAt) {

    public static RequestSummaryDto from(RequestSummary summary) {
        return new RequestSummaryDto(
            summary.id(),
            summary.name(),
            summary.state(),
            summary.publicationNumber(),
            summary.createdAt(),
            summary.updatedAt());
    }
}
