package pl.bzdon.requestmanagement.domain.model.statemachine;

import pl.bzdon.requestmanagement.domain.exception.InvalidStateTransitionException;

import java.util.Map;

import static pl.bzdon.requestmanagement.domain.model.statemachine.RequestAction.ACCEPT;
import static pl.bzdon.requestmanagement.domain.model.statemachine.RequestAction.DELETE;
import static pl.bzdon.requestmanagement.domain.model.statemachine.RequestAction.PUBLISH;
import static pl.bzdon.requestmanagement.domain.model.statemachine.RequestAction.REJECT;
import static pl.bzdon.requestmanagement.domain.model.statemachine.RequestAction.VERIFY;
import static pl.bzdon.requestmanagement.domain.model.statemachine.RequestState.ACCEPTED;
import static pl.bzdon.requestmanagement.domain.model.statemachine.RequestState.CREATED;
import static pl.bzdon.requestmanagement.domain.model.statemachine.RequestState.DELETED;
import static pl.bzdon.requestmanagement.domain.model.statemachine.RequestState.PUBLISHED;
import static pl.bzdon.requestmanagement.domain.model.statemachine.RequestState.REJECTED;
import static pl.bzdon.requestmanagement.domain.model.statemachine.RequestState.VERIFIED;

public final class RequestStateMachine {

    private static final Map<RequestState, Map<RequestAction, RequestState>> TRANSITIONS = Map.of(
        CREATED, Map.of(VERIFY, VERIFIED, DELETE, DELETED),
        VERIFIED, Map.of(ACCEPT, ACCEPTED, REJECT, REJECTED),
        ACCEPTED, Map.of(PUBLISH, PUBLISHED, REJECT, REJECTED)
    );

    public RequestState next(RequestState from, RequestAction action) {
        RequestState to = TRANSITIONS.getOrDefault(from, Map.of()).get(action);
        if (to == null) {
            throw new InvalidStateTransitionException(from, action);
        }
        return to;
    }
}
