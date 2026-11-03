package pl.bzdon.requestmanagement.domain.model;

import lombok.AccessLevel;
import lombok.Getter;
import pl.bzdon.requestmanagement.domain.exception.EditNotAllowedException;
import pl.bzdon.requestmanagement.domain.model.statemachine.RequestAction;
import pl.bzdon.requestmanagement.domain.model.statemachine.RequestState;
import pl.bzdon.requestmanagement.domain.model.statemachine.RequestStateMachine;
import pl.bzdon.requestmanagement.domain.validation.RequiredFieldsValidator;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.LongSupplier;

@Getter
public class Request {

    public static final int MAX_NAME_LENGTH = 255;
    public static final int MAX_BODY_LENGTH = 10_000;

    private static final RequestStateMachine STATE_MACHINE = new RequestStateMachine();

    private final UUID id;
    private final String name;
    private String body;
    private RequestState state;
    private Long publicationNumber;
    private final Instant createdAt;
    private Instant updatedAt;
    @Getter(AccessLevel.NONE)
    private final List<HistoryEntry> history;

    public static Request create(String name, String body) {
        Instant now = Instant.now();
        RequiredFieldsValidator.requireText(name, "name", MAX_NAME_LENGTH);
        RequiredFieldsValidator.requireText(body, "body", MAX_BODY_LENGTH);
        return new Request(UUID.randomUUID(), name, body, RequestState.CREATED, null, now, now, new ArrayList<>());
    }

    public static Request restore(UUID id, String name,
                                  String body, RequestState state,
                                  Long publicationNumber, Instant createdAt,
                                  Instant updatedAt, List<HistoryEntry> history) {
        return new Request(id, name, body, state, publicationNumber, createdAt, updatedAt, new ArrayList<>(history));
    }

    private Request(UUID id, String name, String body, RequestState state, Long publicationNumber,
                    Instant createdAt, Instant updatedAt, List<HistoryEntry> history) {
        this.id = id;
        this.name = name;
        this.body = body;
        this.state = state;
        this.publicationNumber = publicationNumber;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.history = history;
    }

    public void verify() {
        changeState(RequestAction.VERIFY, null);
    }

    public void accept() {
        changeState(RequestAction.ACCEPT, null);
    }

    public void reject(String reason) {
        changeState(RequestAction.REJECT, reason);
    }

    public void delete(String reason) {
        changeState(RequestAction.DELETE, reason);
    }

    public void publish(LongSupplier publicationNumbers) {
        changeState(RequestAction.PUBLISH, null);
        this.publicationNumber = publicationNumbers.getAsLong();
    }

    public void editBody(String newBody) {
        if (!state.isContentEditable()) {
            throw new EditNotAllowedException(state);
        }
        RequiredFieldsValidator.requireText(newBody, "body", MAX_BODY_LENGTH);
        this.body = newBody;
        this.updatedAt = Instant.now();
    }

    private void changeState(RequestAction action, String reason) {
        Instant now = Instant.now();
        RequestState target = STATE_MACHINE.next(state, action);
        if (action.requiresReason()) {
            RequiredFieldsValidator.requireText(reason, "reason");
        }
        history.add(new HistoryEntry(state, target, action, reason, now));
        this.state = target;
        this.updatedAt = now;
    }

    public List<HistoryEntry> getHistory() {
        return List.copyOf(history);
    }
}
