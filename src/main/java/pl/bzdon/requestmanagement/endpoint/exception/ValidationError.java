package pl.bzdon.requestmanagement.endpoint.exception;

public record ValidationError(String field, String message) {
}
