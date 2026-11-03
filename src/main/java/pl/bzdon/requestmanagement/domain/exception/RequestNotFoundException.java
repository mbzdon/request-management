package pl.bzdon.requestmanagement.domain.exception;

import lombok.Getter;

import java.util.UUID;

@Getter
public class RequestNotFoundException extends DomainException {

    private final UUID id;

    public RequestNotFoundException(UUID id) {
        super("Request %s not found".formatted(id));
        this.id = id;
    }

}
