package pl.bzdon.requestmanagement.endpoint.controller;

import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import pl.bzdon.requestmanagement.domain.model.statemachine.RequestAction;
import pl.bzdon.requestmanagement.endpoint.dto.CreateRequestDto;
import pl.bzdon.requestmanagement.endpoint.dto.EditBodyDto;
import pl.bzdon.requestmanagement.endpoint.dto.HistoryEntryDto;
import pl.bzdon.requestmanagement.endpoint.dto.RequestDto;
import pl.bzdon.requestmanagement.endpoint.dto.RequestSummaryDto;
import pl.bzdon.requestmanagement.endpoint.dto.TransitionDto;
import pl.bzdon.requestmanagement.endpoint.pagination.PageDto;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class RequestApi {

    static final String REQUESTS = "/api/requests";
    static final String DEFAULT_BODY = "Test-Body";

    private final MockMvcTester mvc;
    private final JsonMapper jsonMapper;

    RequestApi(MockMvcTester mvc, JsonMapper jsonMapper) {
        this.mvc = mvc;
        this.jsonMapper = jsonMapper;
    }

    RequestDto create(String name) {
        return create(name, DEFAULT_BODY);
    }

    RequestDto create(String name, String body) {
        return read(tryCreate(new CreateRequestDto(name, body)), RequestDto.class);
    }

    RequestDto accepted(String name) {
        UUID id = create(name).id();
        transition(id, RequestAction.VERIFY);
        return transition(id, RequestAction.ACCEPT);
    }

    RequestDto transition(UUID id, RequestAction action) {
        return transition(id, action, null);
    }

    RequestDto transition(UUID id, RequestAction action, String reason) {
        return read(tryTransition(id, action, reason), RequestDto.class);
    }

    RequestDto editBody(UUID id, String body) {
        return read(tryEditBody(id, body), RequestDto.class);
    }

    RequestDto get(UUID id) {
        return read(mvc.get().uri(REQUESTS + "/{id}", id).exchange(), RequestDto.class);
    }

    List<HistoryEntryDto> history(UUID id) {
        return read(mvc.get().uri(REQUESTS + "/{id}/history", id).exchange(), new TypeReference<>() {
        });
    }

    PageDto<RequestSummaryDto> list(String query) {
        return read(tryList(query), new TypeReference<>() {
        });
    }

    MvcTestResult tryCreate(Object request) {
        return mvc.post().uri(REQUESTS).contentType(MediaType.APPLICATION_JSON).content(json(request)).exchange();
    }

    MvcTestResult tryTransition(UUID id, RequestAction action) {
        return tryTransition(id, action, null);
    }

    MvcTestResult tryTransition(UUID id, RequestAction action, String reason) {
        return tryTransition(id, new TransitionDto(action, reason));
    }

    MvcTestResult tryTransition(UUID id, Object request) {
        return mvc.post().uri(REQUESTS + "/{id}/transitions", id)
            .contentType(MediaType.APPLICATION_JSON)
            .content(json(request))
            .exchange();
    }

    MvcTestResult tryEditBody(UUID id, String body) {
        return mvc.patch().uri(REQUESTS + "/{id}", id)
            .contentType(MediaType.APPLICATION_JSON)
            .content(json(new EditBodyDto(body)))
            .exchange();
    }

    MvcTestResult tryGet(String path) {
        return mvc.get().uri(path).exchange();
    }

    MvcTestResult tryList(String query) {
        return mvc.get().uri(query.isEmpty() ? REQUESTS : REQUESTS + "?" + query).exchange();
    }

    private String json(Object request) {
        return request instanceof String raw ? raw : jsonMapper.writeValueAsString(request);
    }

    private <T> T read(MvcTestResult result, Class<T> type) {
        assertThat(result).hasStatus2xxSuccessful();
        return jsonMapper.readValue(result.getResponse().getContentAsByteArray(), type);
    }

    private <T> T read(MvcTestResult result, TypeReference<T> type) {
        assertThat(result).hasStatus2xxSuccessful();
        return jsonMapper.readValue(result.getResponse().getContentAsByteArray(), type);
    }
}
