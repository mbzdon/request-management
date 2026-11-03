package pl.bzdon.requestmanagement.domain.query;

import pl.bzdon.requestmanagement.domain.model.statemachine.RequestState;

public record RequestFilter(String name, RequestState state) {

    public static RequestFilter none() {
        return new RequestFilter(null, null);
    }

    public boolean hasName() {
        return name != null && !name.isBlank();
    }

    public boolean hasState() {
        return state != null;
    }
}
