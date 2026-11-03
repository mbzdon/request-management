package pl.bzdon.requestmanagement.endpoint.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import pl.bzdon.requestmanagement.domain.query.PageQuery;
import pl.bzdon.requestmanagement.domain.query.PageResult;
import pl.bzdon.requestmanagement.domain.model.Request;
import pl.bzdon.requestmanagement.domain.model.statemachine.RequestAction;
import pl.bzdon.requestmanagement.domain.query.RequestFilter;
import pl.bzdon.requestmanagement.domain.model.statemachine.RequestState;
import pl.bzdon.requestmanagement.domain.service.RequestService;
import pl.bzdon.requestmanagement.endpoint.pagination.PaginationProperties;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Web layer only (service mocked): request mapping and error translation.
 * The end-to-end behaviour is covered by the integration tests.
 */
@WebMvcTest(RequestController.class)
@EnableConfigurationProperties(PaginationProperties.class)
class RequestControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RequestService requestService;

    @Test
    void shouldTranslateConcurrentModificationToConflict() throws Exception {
        UUID id = UUID.randomUUID();
        given(requestService.changeState(id, RequestAction.VERIFY, null))
            .willThrow(new OptimisticLockingFailureException("version mismatch"));

        mockMvc.perform(post("/api/requests/{id}/transitions", id)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"action": "VERIFY"}
                    """))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.title").value("Concurrent modification"));
    }

    @Test
    void shouldTranslateUnexpectedErrorToProblemDetailWithoutInternalMessage() throws Exception {
        UUID id = UUID.randomUUID();
        given(requestService.getById(id)).willThrow(new IllegalStateException("connection pool exhausted"));

        mockMvc.perform(get("/api/requests/{id}", id))
            .andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.title").value("Unexpected error"))
            .andExpect(jsonPath("$.detail").value("An unexpected error occurred"));
    }

    @Test
    void shouldPassFiltersAndResolvedPageToService() throws Exception {
        given(requestService.findAll(any(), any())).willReturn(new PageResult<>(List.of(), 2, 10, 0));

        mockMvc.perform(get("/api/requests")
                .param("name", "holiday")
                .param("state", "VERIFIED")
                .param("page", "2"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.page").value(2))
            .andExpect(jsonPath("$.size").value(10));

        verify(requestService).findAll(new RequestFilter("holiday", RequestState.VERIFIED), new PageQuery(2, 10));
    }

    @Test
    void shouldPassReasonToService() throws Exception {
        UUID id = UUID.randomUUID();
        given(requestService.changeState(id, RequestAction.DELETE, "Duplicate"))
            .willReturn(Request.create("Test-Name", "Test-Body"));

        mockMvc.perform(post("/api/requests/{id}/transitions", id)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"action": "DELETE", "reason": "Duplicate"}
                    """))
            .andExpect(status().isOk());

        verify(requestService).changeState(id, RequestAction.DELETE, "Duplicate");
    }

    @Test
    void shouldReturnBadRequestForMalformedId() throws Exception {
        mockMvc.perform(get("/api/requests/not-a-uuid"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void shouldReturnBadRequestForMalformedJson() throws Exception {
        mockMvc.perform(post("/api/requests/{id}/transitions", UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{not json"))
            .andExpect(status().isBadRequest());
        verifyNoInteractions(requestService);
    }
}
