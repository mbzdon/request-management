package pl.bzdon.requestmanagement.infrastructure.mapper;

import lombok.experimental.UtilityClass;
import pl.bzdon.requestmanagement.domain.model.HistoryEntry;
import pl.bzdon.requestmanagement.domain.model.Request;
import pl.bzdon.requestmanagement.infrastructure.entity.HistoryEntryEmbeddable;
import pl.bzdon.requestmanagement.infrastructure.entity.RequestEntity;

import java.util.List;

@UtilityClass
public class RequestMapper {

    public Request toDomain(RequestEntity entity) {
        return Request.restore(
            entity.getId(),
            entity.getName(),
            entity.getBody(),
            entity.getState(),
            entity.getPublicationNumber(),
            entity.getCreatedAt(),
            entity.getUpdatedAt(),
            entity.getHistory().stream().map(RequestMapper::toDomain).toList());
    }

    public void copyToEntity(Request request, RequestEntity entity) {
        entity.setName(request.getName());
        entity.setBody(request.getBody());
        entity.setState(request.getState());
        entity.setPublicationNumber(request.getPublicationNumber());
        entity.setCreatedAt(request.getCreatedAt());
        entity.setUpdatedAt(request.getUpdatedAt());

        List<HistoryEntryEmbeddable> persisted = entity.getHistory();
        request.getHistory().stream()
            .skip(persisted.size())
            .map(RequestMapper::toEmbeddable)
            .forEach(persisted::add);
    }

    private HistoryEntry toDomain(HistoryEntryEmbeddable entry) {
        return new HistoryEntry(entry.getFromState(), entry.getToState(), entry.getAction(), entry.getReason(), entry.getOccurredAt());
    }

    private HistoryEntryEmbeddable toEmbeddable(HistoryEntry entry) {
        return new HistoryEntryEmbeddable(entry.from(), entry.to(), entry.action(), entry.reason(), entry.occurredAt());
    }
}
