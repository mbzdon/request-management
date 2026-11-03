package pl.bzdon.requestmanagement.domain.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import pl.bzdon.requestmanagement.domain.exception.EditNotAllowedException;
import pl.bzdon.requestmanagement.domain.exception.InvalidStateTransitionException;
import pl.bzdon.requestmanagement.domain.exception.MissingRequiredFieldException;
import pl.bzdon.requestmanagement.domain.exception.RequestNotFoundException;
import pl.bzdon.requestmanagement.domain.model.HistoryEntry;
import pl.bzdon.requestmanagement.domain.query.PageQuery;
import pl.bzdon.requestmanagement.domain.query.PageResult;
import pl.bzdon.requestmanagement.domain.model.Request;
import pl.bzdon.requestmanagement.domain.model.statemachine.RequestAction;
import pl.bzdon.requestmanagement.domain.query.RequestFilter;
import pl.bzdon.requestmanagement.domain.query.RequestSummary;
import pl.bzdon.requestmanagement.domain.model.statemachine.RequestState;

import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static pl.bzdon.requestmanagement.domain.model.statemachine.RequestAction.ACCEPT;
import static pl.bzdon.requestmanagement.domain.model.statemachine.RequestAction.DELETE;
import static pl.bzdon.requestmanagement.domain.model.statemachine.RequestAction.PUBLISH;
import static pl.bzdon.requestmanagement.domain.model.statemachine.RequestAction.REJECT;
import static pl.bzdon.requestmanagement.domain.model.statemachine.RequestAction.VERIFY;

class RequestServiceTest {

    private final TestRequestRepository repository = new TestRequestRepository();
    private final RequestService service = new RequestService(repository);

    @Test
    void shouldCreateAndStoreRequest() {
        Request created = service.create("Test-Name", "Test-Body");

        assertThat(created.getState()).isEqualTo(RequestState.CREATED);
        assertThat(repository.findById(created.getId())).contains(created);
    }

    @Test
    void shouldNotStoreInvalidRequest() {
        assertThatThrownBy(() -> service.create("Test-Name", " "))
            .isInstanceOf(MissingRequiredFieldException.class);
        assertThat(repository.saveCount()).isZero();
    }

    @Test
    void shouldEditBodyAndStoreRequest() {
        UUID id = service.create("Test-Name", "Test-Body").getId();

        Request edited = service.editBody(id, "New content");

        assertThat(edited.getBody()).isEqualTo("New content");
        assertThat(repository.saveCount()).isEqualTo(2);
    }

    @Test
    void shouldNotStoreRequestWhenEditIsNotAllowed() {
        UUID id = acceptedRequest();
        int savesBefore = repository.saveCount();

        assertThatThrownBy(() -> service.editBody(id, "New content"))
            .isInstanceOf(EditNotAllowedException.class);
        assertThat(repository.saveCount()).isEqualTo(savesBefore);
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @MethodSource("pathsThroughDiagram")
    void shouldApplyEveryActionOfTheDiagram(List<RequestAction> actions, RequestState expectedState) {
        UUID id = service.create("Test-Name", "Test-Body").getId();

        actions.forEach(action -> service.changeState(id, action, action.requiresReason() ? "Reason" : null));

        assertThat(service.getById(id).getState()).isEqualTo(expectedState);
        assertThat(service.getHistory(id)).extracting(HistoryEntry::action).containsExactlyElementsOf(actions);
    }

    static Stream<Arguments> pathsThroughDiagram() {
        return Stream.of(
            Arguments.of(List.of(DELETE), RequestState.DELETED),
            Arguments.of(List.of(VERIFY), RequestState.VERIFIED),
            Arguments.of(List.of(VERIFY, REJECT), RequestState.REJECTED),
            Arguments.of(List.of(VERIFY, ACCEPT), RequestState.ACCEPTED),
            Arguments.of(List.of(VERIFY, ACCEPT, REJECT), RequestState.REJECTED),
            Arguments.of(List.of(VERIFY, ACCEPT, PUBLISH), RequestState.PUBLISHED));
    }

    @Test
    void shouldAssignPublicationNumberOnPublish() {
        UUID id = acceptedRequest();

        Request published = service.changeState(id, RequestAction.PUBLISH, null);

        assertThat(published.getPublicationNumber()).isEqualTo(1L);
    }

    @Test
    void shouldAssignDifferentNumbersToPublishedRequests() {
        UUID first = acceptedRequest();
        UUID second = acceptedRequest();

        long firstNumber = service.changeState(first, RequestAction.PUBLISH, null).getPublicationNumber();
        long secondNumber = service.changeState(second, RequestAction.PUBLISH, null).getPublicationNumber();

        assertThat(firstNumber).isNotEqualTo(secondNumber);
    }

    @Test
    void shouldNotConsumePublicationNumberWhenPublishIsNotAllowed() {
        UUID id = service.create("Test-Name", "Test-Body").getId();

        assertThatThrownBy(() -> service.changeState(id, RequestAction.PUBLISH, null))
            .isInstanceOf(InvalidStateTransitionException.class);
        assertThat(repository.issuedPublicationNumbers()).isZero();
    }

    @Test
    void shouldPassReasonToDomain() {
        UUID id = service.create("Test-Name", "Test-Body").getId();

        service.changeState(id, RequestAction.DELETE, "Duplicate");

        assertThat(service.getHistory(id))
            .extracting(HistoryEntry::action, HistoryEntry::reason)
            .containsExactly(tuple(RequestAction.DELETE, "Duplicate"));
    }

    @Test
    void shouldThrowNotFoundForUnknownRequest() {
        UUID unknown = UUID.randomUUID();

        assertThatThrownBy(() -> service.getById(unknown))
            .isInstanceOfSatisfying(RequestNotFoundException.class, ex -> assertThat(ex.getId()).isEqualTo(unknown));
        assertThatThrownBy(() -> service.getHistory(unknown)).isInstanceOf(RequestNotFoundException.class);
        assertThatThrownBy(() -> service.editBody(unknown, "Test-Body")).isInstanceOf(RequestNotFoundException.class);
        assertThatThrownBy(() -> service.changeState(unknown, RequestAction.VERIFY, null))
            .isInstanceOf(RequestNotFoundException.class);
    }

    @Test
    void shouldDelegateBrowsingToRepository() {
        service.create("Test-Name", "Test-Body");
        service.create("Name", "Test-Body");

        PageResult<RequestSummary> result = service.findAll(new RequestFilter("test", null), new PageQuery(0, 10));

        assertThat(result.content()).extracting(RequestSummary::name).containsExactly("Test-Name");
        assertThat(result.totalElements()).isEqualTo(1);
    }

    private UUID acceptedRequest() {
        UUID id = service.create("Test-Name", "Test-Body").getId();
        service.changeState(id, RequestAction.VERIFY, null);
        service.changeState(id, RequestAction.ACCEPT, null);
        return id;
    }
}
