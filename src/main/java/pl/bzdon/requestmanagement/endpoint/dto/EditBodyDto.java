package pl.bzdon.requestmanagement.endpoint.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import pl.bzdon.requestmanagement.domain.model.Request;

public record EditBodyDto(@NotBlank @Size(max = Request.MAX_BODY_LENGTH) String body) {
}
