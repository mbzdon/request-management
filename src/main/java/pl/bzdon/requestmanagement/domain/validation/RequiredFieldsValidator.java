package pl.bzdon.requestmanagement.domain.validation;

import pl.bzdon.requestmanagement.domain.exception.FieldTooLongException;
import pl.bzdon.requestmanagement.domain.exception.MissingRequiredFieldException;

public final class RequiredFieldsValidator {

    private RequiredFieldsValidator() {
    }

    public static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new MissingRequiredFieldException(field);
        }
    }

    public static void requireText(String value, String field, int maxLength) {
        requireText(value, field);
        if (value.length() > maxLength) {
            throw new FieldTooLongException(field, maxLength);
        }
    }
}
