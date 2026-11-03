package pl.bzdon.requestmanagement.endpoint.dto;

import pl.bzdon.requestmanagement.domain.model.Request;
import pl.bzdon.requestmanagement.domain.model.statemachine.RequestState;

import java.time.Instant;
import java.util.UUID;

public record RequestDto(
    UUID id,
    String name,
    String body,
    RequestState state,
    Long publicationNumber,
    Instant createdAt,
    Instant updatedAt) {

    public static RequestDto from(Request request) {
        return new RequestDto(
            request.getId(),
            request.getName(),
            request.getBody(),
            request.getState(),
            request.getPublicationNumber(),
            request.getCreatedAt(),
            request.getUpdatedAt());
    }
}
