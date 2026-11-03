package pl.bzdon.requestmanagement.domain.model.statemachine;

public enum RequestState {
    CREATED,
    VERIFIED,
    ACCEPTED,
    PUBLISHED,
    REJECTED,
    DELETED;

    public boolean isContentEditable() {
        return this == CREATED || this == VERIFIED;
    }
}
