package pl.bzdon.requestmanagement.domain.exception;

import lombok.Getter;
import pl.bzdon.requestmanagement.domain.model.statemachine.RequestState;

@Getter
public class EditNotAllowedException extends DomainException {

    private final RequestState state;

    public EditNotAllowedException(RequestState state) {
        super("Body cannot be edited in state %s".formatted(state));
        this.state = state;
    }

}
