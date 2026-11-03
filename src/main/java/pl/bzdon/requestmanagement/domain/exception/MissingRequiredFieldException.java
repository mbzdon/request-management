package pl.bzdon.requestmanagement.domain.exception;

import lombok.Getter;

@Getter
public class MissingRequiredFieldException extends DomainException {

    private final String field;

    public MissingRequiredFieldException(String field) {
        super("Field '%s' is required".formatted(field));
        this.field = field;
    }

}
