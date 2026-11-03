package pl.bzdon.requestmanagement.domain.exception;

import lombok.Getter;
import pl.bzdon.requestmanagement.domain.model.statemachine.RequestAction;
import pl.bzdon.requestmanagement.domain.model.statemachine.RequestState;

@Getter
public class InvalidStateTransitionException extends DomainException {

    private final RequestState from;
    private final RequestAction action;

    public InvalidStateTransitionException(RequestState from, RequestAction action) {
        super("Action %s is not allowed in state %s".formatted(action, from));
        this.from = from;
        this.action = action;
    }

}
