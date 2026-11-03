package pl.bzdon.requestmanagement.domain.model.statemachine;

public enum RequestAction {
    VERIFY,
    ACCEPT,
    REJECT,
    DELETE,
    PUBLISH;

    public boolean requiresReason() {
        return this == REJECT || this == DELETE;
    }
}
