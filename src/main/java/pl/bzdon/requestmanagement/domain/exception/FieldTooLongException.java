package pl.bzdon.requestmanagement.domain.exception;

import lombok.Getter;

@Getter
public class FieldTooLongException extends DomainException {

    private final String field;
    private final int maxLength;

    public FieldTooLongException(String field, int maxLength) {
        super("Field '%s' must have at most %d characters".formatted(field, maxLength));
        this.field = field;
        this.maxLength = maxLength;
    }
}
