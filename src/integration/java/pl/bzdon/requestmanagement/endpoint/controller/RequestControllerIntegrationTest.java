package pl.bzdon.requestmanagement.endpoint.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import pl.bzdon.requestmanagement.IntegrationTest;
import pl.bzdon.requestmanagement.domain.model.statemachine.RequestAction;
import pl.bzdon.requestmanagement.endpoint.dto.CreateRequestDto;
import pl.bzdon.requestmanagement.endpoint.dto.HistoryEntryDto;
import pl.bzdon.requestmanagement.endpoint.dto.RequestDto;
import pl.bzdon.requestmanagement.endpoint.dto.RequestSummaryDto;
import pl.bzdon.requestmanagement.endpoint.pagination.PageDto;
import tools.jackson.databind.json.JsonMapper;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static pl.bzdon.requestmanagement.domain.model.statemachine.RequestAction.ACCEPT;
import static pl.bzdon.requestmanagement.domain.model.statemachine.RequestAction.DELETE;
import static pl.bzdon.requestmanagement.domain.model.statemachine.RequestAction.PUBLISH;
import static pl.bzdon.requestmanagement.domain.model.statemachine.RequestAction.REJECT;
import static pl.bzdon.requestmanagement.domain.model.statemachine.RequestAction.VERIFY;
import static pl.bzdon.requestmanagement.domain.model.statemachine.RequestState.ACCEPTED;
import static pl.bzdon.requestmanagement.domain.model.statemachine.RequestState.CREATED;
import static pl.bzdon.requestmanagement.domain.model.statemachine.RequestState.PUBLISHED;
import static pl.bzdon.requestmanagement.domain.model.statemachine.RequestState.REJECTED;
import static pl.bzdon.requestmanagement.domain.model.statemachine.RequestState.VERIFIED;
import static pl.bzdon.requestmanagement.endpoint.controller.RequestApi.REQUESTS;

class RequestControllerIntegrationTest extends IntegrationTest {

    @Autowired
    private MockMvcTester mvc;

    @Autowired
    private JsonMapper jsonMapper;

    private RequestApi api;

    @BeforeEach
    void setUpApi() {
        api = new RequestApi(mvc, jsonMapper);
    }

    @Test
    void shouldCreateRequest() {
        MvcTestResult result = api.tryCreate(new CreateRequestDto("Test-Name", "Test-Body"));

        assertThat(result).hasStatus(HttpStatus.CREATED);
        RequestDto created = jsonMapper.readValue(result.getResponse().getContentAsByteArray(), RequestDto.class);
        assertThat(created.id()).isNotNull();
        assertThat(created.name()).isEqualTo("Test-Name");
        assertThat(created.body()).isEqualTo("Test-Body");
        assertThat(created.state()).isEqualTo(CREATED);
        assertThat(created.publicationNumber()).isNull();
        assertThat(result.getResponse().getHeader("Location")).endsWith(REQUESTS + "/" + created.id());
    }

    @Test
    void shouldReturnCreatedRequestUnderLocation() {
        String location = api.tryCreate(new CreateRequestDto("Test-Name", "Test-Body")).getResponse().getHeader("Location");

        assertThat(api.tryGet(location))
            .hasStatusOk()
            .bodyJson().extractingPath("$.name").isEqualTo("Test-Name");
    }

    @ParameterizedTest
    @ValueSource(strings = {
        """
            {"body": "Test-Body"}""",
        """
            {"name": "Test-Name"}""",
        """
            {"name": "  ", "body": "Test-Body"}""",
        """
            {"name": "Test-Name", "body": ""}"""
    })
    void shouldRejectCreateWithoutNameOrBody(String json) {
        assertThat(api.tryCreate(json))
            .hasStatus(HttpStatus.BAD_REQUEST)
            .bodyJson().extractingPath("$.errors").asArray().hasSize(1);
    }

    @Test
    void shouldListEveryInvalidFieldOnCreate() {
        assertThat(api.tryCreate("{}"))
            .hasStatus(HttpStatus.BAD_REQUEST)
            .bodyJson().extractingPath("$.errors[*].field").asArray().containsExactly("body", "name");
    }

    @Test
    void shouldRejectNameLongerThanLimit() {
        assertThat(api.tryCreate(new CreateRequestDto("x".repeat(256), "Test-Body")))
            .hasStatus(HttpStatus.BAD_REQUEST)
            .bodyJson().extractingPath("$.errors[0].field").isEqualTo("name");
    }

    @Test
    void shouldRejectBodyLongerThanLimit() {
        assertThat(api.tryCreate(new CreateRequestDto("Test-Name", "x".repeat(10_001))))
            .hasStatus(HttpStatus.BAD_REQUEST)
            .bodyJson().extractingPath("$.errors[0].field").isEqualTo("body");
    }

    @Test
    void shouldReturnNotFoundForUnknownRequest() {
        String unknownId = "00000000-0000-0000-0000-000000000000";

        assertThat(api.tryGet(REQUESTS + "/" + unknownId))
            .hasStatus(HttpStatus.NOT_FOUND)
            .bodyJson().extractingPath("$.requestId").isEqualTo(unknownId);
    }

    @Test
    void shouldPublishAcceptedRequestAndRecordHistory() {
        UUID id = api.create("Test-Name").id();

        assertThat(api.transition(id, VERIFY).state()).isEqualTo(VERIFIED);
        assertThat(api.transition(id, ACCEPT).state()).isEqualTo(ACCEPTED);
        RequestDto published = api.transition(id, PUBLISH);

        assertThat(published.state()).isEqualTo(PUBLISHED);
        assertThat(published.publicationNumber()).isNotNull();
        assertThat(api.history(id))
            .extracting(HistoryEntryDto::from, HistoryEntryDto::to, HistoryEntryDto::action)
            .containsExactly(
                tuple(CREATED, VERIFIED, VERIFY),
                tuple(VERIFIED, ACCEPTED, ACCEPT),
                tuple(ACCEPTED, PUBLISHED, PUBLISH));
        assertThat(api.history(id)).allSatisfy(entry -> assertThat(entry.occurredAt()).isNotNull());
    }

    @Test
    void shouldRejectTransitionOutsideDiagram() {
        UUID id = api.create("Test-Name").id();

        assertThat(api.tryTransition(id, PUBLISH))
            .hasStatus(HttpStatus.CONFLICT)
            .bodyJson()
            .satisfies(body -> {
                assertThat(body).extractingPath("$.currentState").isEqualTo("CREATED");
                assertThat(body).extractingPath("$.action").isEqualTo("PUBLISH");
            });
    }

    @Test
    void shouldNotAllowAnyTransitionFromTerminalState() {
        UUID id = api.create("Test-Name").id();
        api.transition(id, DELETE, "Duplicate");

        assertThat(api.tryTransition(id, VERIFY)).hasStatus(HttpStatus.CONFLICT);
    }

    @ParameterizedTest
    @EnumSource(value = RequestAction.class, names = {"DELETE", "REJECT"})
    void shouldRequireReasonToDeleteOrReject(RequestAction action) {
        UUID id = api.create("Test-Name").id();
        if (action == REJECT) {
            api.transition(id, VERIFY);
        }

        assertThat(api.tryTransition(id, action, null))
            .hasStatus(HttpStatus.BAD_REQUEST)
            .bodyJson().extractingPath("$.field").isEqualTo("reason");
        assertThat(api.tryTransition(id, action, "  ")).hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    void shouldStoreReasonInHistory() {
        UUID id = api.accepted("Test-Name").id();

        assertThat(api.transition(id, REJECT, "Budget exceeded").state()).isEqualTo(REJECTED);
        assertThat(api.history(id).getLast())
            .extracting(HistoryEntryDto::action, HistoryEntryDto::reason)
            .containsExactly(REJECT, "Budget exceeded");
    }

    @Test
    void shouldRejectUnknownOrMissingAction() {
        UUID id = api.create("Test-Name").id();

        assertThat(api.tryTransition(id, """
            {"action": "APPROVE"}""")).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(api.tryTransition(id, "{}"))
            .hasStatus(HttpStatus.BAD_REQUEST)
            .bodyJson().extractingPath("$.errors[0].field").isEqualTo("action");
    }

    @Test
    void shouldNotConsumePublicationNumberOnRejectedPublish() {
        UUID first = api.accepted("First").id();
        UUID notAccepted = api.create("Not accepted").id();
        UUID second = api.accepted("Second").id();

        long firstNumber = api.transition(first, PUBLISH).publicationNumber();
        assertThat(api.tryTransition(notAccepted, PUBLISH)).hasStatus(HttpStatus.CONFLICT);
        long secondNumber = api.transition(second, PUBLISH).publicationNumber();

        assertThat(secondNumber).isEqualTo(firstNumber + 1);
    }

    @Test
    void shouldEditBodyWhileCreatedOrVerified() {
        UUID id = api.create("Test-Name").id();

        assertThat(api.editBody(id, "Changed while created").body()).isEqualTo("Changed while created");

        api.transition(id, VERIFY);
        assertThat(api.editBody(id, "Changed while verified").body()).isEqualTo("Changed while verified");
    }

    @Test
    void shouldNotEditBodyAfterAcceptance() {
        UUID id = api.accepted("Test-Name").id();

        assertThat(api.tryEditBody(id, "Too late"))
            .hasStatus(HttpStatus.CONFLICT)
            .bodyJson().extractingPath("$.currentState").isEqualTo("ACCEPTED");
        assertThat(api.get(id).body()).isEqualTo(RequestApi.DEFAULT_BODY);
    }

    @Test
    void shouldRejectBlankBodyOnEdit() {
        UUID id = api.create("Test-Name").id();

        assertThat(api.tryEditBody(id, " ")).hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    void shouldUseDefaultPageSizeOfTen() {
        for (int i = 0; i < 12; i++) {
            api.create("Request " + i);
        }

        PageDto<RequestSummaryDto> firstPage = api.list("");
        assertThat(firstPage.content()).hasSize(10);
        assertThat(firstPage.page()).isZero();
        assertThat(firstPage.size()).isEqualTo(10);
        assertThat(firstPage.totalElements()).isEqualTo(12);
        assertThat(firstPage.totalPages()).isEqualTo(2);

        assertThat(api.list("page=1").content()).hasSize(2);
    }

    @Test
    void shouldCapRequestedPageSize() {
        assertThat(api.list("size=1000").size()).isEqualTo(100);
    }

    @Test
    void shouldRejectInvalidPagingParameters() {
        assertThat(api.tryList("page=-1"))
            .hasStatus(HttpStatus.BAD_REQUEST)
            .bodyJson().extractingPath("$.errors[0].field").isEqualTo("page");
        assertThat(api.tryList("size=0"))
            .hasStatus(HttpStatus.BAD_REQUEST)
            .bodyJson().extractingPath("$.errors[0].field").isEqualTo("size");
        assertThat(api.tryList("state=UNKNOWN")).hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    void shouldFilterByNameAndState() {
        UUID summerHoliday = api.create("Summer holiday").id();
        api.transition(summerHoliday, VERIFY);
        api.create("Winter holiday");
        api.create("Laptop");

        assertThat(api.list("name=HOLIDAY").totalElements()).isEqualTo(2);
        assertThat(api.list("name=holiday&state=VERIFIED").content())
            .extracting(RequestSummaryDto::name, RequestSummaryDto::state)
            .containsExactly(tuple("Summer holiday", VERIFIED));
    }

    @Test
    void shouldListSummariesWithoutBody() {
        api.create("Test-Name");

        assertThat(api.tryList(""))
            .hasStatusOk()
            .bodyJson().extractingPath("$.content[0]").asMap().doesNotContainKey("body");
    }

    @Test
    void shouldExposeHealthAndApiDocs() {
        assertThat(api.tryGet("/actuator/health"))
            .hasStatusOk()
            .bodyJson().extractingPath("$.status").isEqualTo("UP");
        assertThat(api.tryGet("/v3/api-docs"))
            .hasStatusOk()
            .bodyJson()
            .satisfies(docs -> {
                assertThat(docs).extractingPath("$.info.title").isEqualTo("Request Management API");
                assertThat(docs).extractingPath("$.paths['/api/requests'].post.tags[0]").isEqualTo("Requests");
                assertThat(docs).extractingPath("$.paths['/api/requests'].post.responses").asMap()
                    .containsOnlyKeys("201", "400");
                assertThat(docs).extractingPath("$.paths['/api/requests'].post.responses['201'].content").asMap()
                    .containsOnlyKeys("application/json");
                assertThat(docs).extractingPath("$.paths['/api/requests/{id}/transitions'].post.responses").asMap()
                    .containsOnlyKeys("200", "400", "404", "409");
                assertThat(docs).extractingPath("$.components.responses.NotFound.content").asMap()
                    .containsOnlyKeys("application/problem+json");
            });
    }
}
