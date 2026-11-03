package pl.bzdon.requestmanagement.endpoint.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(info = @Info(
    title = "Request Management API",
    version = "1.0",
    description = "Lifecycle of requests: create, edit, state transitions, publication, audit log and browsing."))
public class OpenApiConfiguration {

    public static final String BAD_REQUEST = "#/components/responses/BadRequest";
    public static final String NOT_FOUND = "#/components/responses/NotFound";
    public static final String CONFLICT = "#/components/responses/Conflict";

    private static final String PROBLEM_JSON = "application/problem+json";
    private static final String PROBLEM_SCHEMA = "#/components/schemas/ProblemDetail";

    @Bean
    OpenApiCustomizer errorResponses() {
        return openApi -> openApi.getComponents()
            .addSchemas("ProblemDetail", problemDetailSchema())
            .addResponses("BadRequest", errorResponse("Invalid input: missing, invalid or too long field, malformed JSON"))
            .addResponses("NotFound", errorResponse("Request does not exist"))
            .addResponses("Conflict", errorResponse("Not allowed in the current state, or modified concurrently"));
    }

    private static Schema<?> problemDetailSchema() {
        return new ObjectSchema()
            .description("RFC 9457 problem details; some errors add properties such as currentState, field or errors")
            .addProperty("type", new StringSchema())
            .addProperty("title", new StringSchema())
            .addProperty("status", new IntegerSchema())
            .addProperty("detail", new StringSchema())
            .addProperty("instance", new StringSchema());
    }

    private static ApiResponse errorResponse(String description) {
        return new ApiResponse()
            .description(description)
            .content(new Content().addMediaType(PROBLEM_JSON, new MediaType().schema(new Schema<>().$ref(PROBLEM_SCHEMA))));
    }
}
