package pl.bzdon.requestmanagement.infrastructure.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import pl.bzdon.requestmanagement.domain.model.statemachine.RequestAction;
import pl.bzdon.requestmanagement.domain.model.statemachine.RequestState;

import java.time.Instant;

@Getter
@Embeddable
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class HistoryEntryEmbeddable {

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RequestState fromState;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RequestState toState;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RequestAction action;

    @Column(columnDefinition = "text")
    private String reason;

    @Column(nullable = false)
    private Instant occurredAt;
}
