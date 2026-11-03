package pl.bzdon.requestmanagement.domain.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import pl.bzdon.requestmanagement.domain.exception.EditNotAllowedException;
import pl.bzdon.requestmanagement.domain.exception.FieldTooLongException;
import pl.bzdon.requestmanagement.domain.exception.InvalidStateTransitionException;
import pl.bzdon.requestmanagement.domain.exception.MissingRequiredFieldException;
import pl.bzdon.requestmanagement.domain.model.statemachine.RequestAction;
import pl.bzdon.requestmanagement.domain.model.statemachine.RequestState;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

class RequestTest {

    @Test
    void shouldCreateRequestInCreatedState() {
        Request request = Request.create("Test-Name", "Test-Body");

        assertThat(request.getId()).isNotNull();
        assertThat(request.getName()).isEqualTo("Test-Name");
        assertThat(request.getBody()).isEqualTo("Test-Body");
        assertThat(request.getState()).isEqualTo(RequestState.CREATED);
        assertThat(request.getPublicationNumber()).isNull();
        assertThat(request.getCreatedAt()).isNotNull().isEqualTo(request.getUpdatedAt());
        assertThat(request.getHistory()).isEmpty();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = "   ")
    void shouldRequireName(String name) {
        assertThatThrownBy(() -> Request.create(name, "Test-Body"))
            .isInstanceOfSatisfying(MissingRequiredFieldException.class,
                ex -> assertThat(ex.getField()).isEqualTo("name"));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = "   ")
    void shouldRequireBody(String body) {
        assertThatThrownBy(() -> Request.create("Test-Name", body))
            .isInstanceOfSatisfying(MissingRequiredFieldException.class,
                ex -> assertThat(ex.getField()).isEqualTo("body"));
    }

    @Test
    void shouldAcceptNameAndBodyAtMaximumLength() {
        Request request = Request.create("x".repeat(Request.MAX_NAME_LENGTH), "x".repeat(Request.MAX_BODY_LENGTH));

        assertThat(request.getName()).hasSize(Request.MAX_NAME_LENGTH);
        assertThat(request.getBody()).hasSize(Request.MAX_BODY_LENGTH);
    }

    @Test
    void shouldRejectNameLongerThanLimit() {
        assertThatThrownBy(() -> Request.create("x".repeat(Request.MAX_NAME_LENGTH + 1), "Test-Body"))
            .isInstanceOfSatisfying(FieldTooLongException.class, ex -> {
                assertThat(ex.getField()).isEqualTo("name");
                assertThat(ex.getMaxLength()).isEqualTo(Request.MAX_NAME_LENGTH);
            });
    }

    @Test
    void shouldRejectBodyLongerThanLimit() {
        assertThatThrownBy(() -> Request.create("Test-Name", "x".repeat(Request.MAX_BODY_LENGTH + 1)))
            .isInstanceOfSatisfying(FieldTooLongException.class,
                ex -> assertThat(ex.getField()).isEqualTo("body"));
    }

    @Test
    void shouldNotEditBodyBeyondLimit() {
        Request request = Request.create("Test-Name", "Test-Body");

        assertThatThrownBy(() -> request.editBody("x".repeat(Request.MAX_BODY_LENGTH + 1)))
            .isInstanceOf(FieldTooLongException.class);
        assertThat(request.getBody()).isEqualTo("Test-Body");
    }

    @Test
    void shouldGoThroughHappyPathAndRecordHistory() {
        Request request = Request.create("Test-Name", "Test-Body");

        request.verify();
        request.accept();
        request.publish(() -> 7L);

        assertThat(request.getState()).isEqualTo(RequestState.PUBLISHED);
        assertThat(request.getPublicationNumber()).isEqualTo(7L);
        assertThat(request.getHistory())
            .extracting(HistoryEntry::from, HistoryEntry::to, HistoryEntry::action, HistoryEntry::reason)
            .containsExactly(
                tuple(RequestState.CREATED, RequestState.VERIFIED, RequestAction.VERIFY, null),
                tuple(RequestState.VERIFIED, RequestState.ACCEPTED, RequestAction.ACCEPT, null),
                tuple(RequestState.ACCEPTED, RequestState.PUBLISHED, RequestAction.PUBLISH, null));
    }

    @Test
    void shouldUpdateTimestampOnTransition() {
        Request request = Request.create("Test-Name", "Test-Body");

        request.verify();

        HistoryEntry entry = request.getHistory().getFirst();
        assertThat(request.getUpdatedAt()).isEqualTo(entry.occurredAt());
        assertThat(request.getUpdatedAt()).isAfterOrEqualTo(request.getCreatedAt());
    }

    @Test
    void shouldDeleteWithReason() {
        Request request = Request.create("Test-Name", "Test-Body");

        request.delete("Duplicate");

        assertThat(request.getState()).isEqualTo(RequestState.DELETED);
        assertThat(request.getHistory().getLast().reason()).isEqualTo("Duplicate");
    }

    @Test
    void shouldRejectVerifiedRequestWithReason() {
        Request request = inState(RequestState.VERIFIED);

        request.reject("Test-Rejection-Reason");

        assertThat(request.getState()).isEqualTo(RequestState.REJECTED);
        assertThat(request.getHistory().getLast().reason()).isEqualTo("Test-Rejection-Reason");
    }

    @Test
    void shouldRejectAcceptedRequestWithReason() {
        Request request = inState(RequestState.ACCEPTED);

        request.reject("Test-Rejection-Reason");

        assertThat(request.getState()).isEqualTo(RequestState.REJECTED);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = "   ")
    void shouldRequireReasonToDelete(String reason) {
        Request request = Request.create("Test-Name", "Test-Body");

        assertThatThrownBy(() -> request.delete(reason))
            .isInstanceOfSatisfying(MissingRequiredFieldException.class,
                ex -> assertThat(ex.getField()).isEqualTo("reason"));
        assertThat(request.getState()).isEqualTo(RequestState.CREATED);
        assertThat(request.getHistory()).isEmpty();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = "   ")
    void shouldRequireReasonToReject(String reason) {
        Request request = inState(RequestState.VERIFIED);

        assertThatThrownBy(() -> request.reject(reason))
            .isInstanceOf(MissingRequiredFieldException.class);
        assertThat(request.getState()).isEqualTo(RequestState.VERIFIED);
    }

    @Test
    void shouldReportInvalidTransitionBeforeMissingReason() {
        Request request = Request.create("Test-Name", "Test-Body");

        assertThatThrownBy(() -> request.reject(null))
            .isInstanceOf(InvalidStateTransitionException.class);
    }

    @Test
    void shouldNotChangeAnythingOnInvalidTransition() {
        Request request = Request.create("Test-Name", "Test-Body");
        Instant updatedAt = request.getUpdatedAt();

        assertThatThrownBy(request::accept)
            .isInstanceOfSatisfying(InvalidStateTransitionException.class, ex -> {
                assertThat(ex.getFrom()).isEqualTo(RequestState.CREATED);
                assertThat(ex.getAction()).isEqualTo(RequestAction.ACCEPT);
            });
        assertThat(request.getState()).isEqualTo(RequestState.CREATED);
        assertThat(request.getUpdatedAt()).isEqualTo(updatedAt);
        assertThat(request.getHistory()).isEmpty();
    }

    @ParameterizedTest
    @EnumSource(value = RequestState.class, names = {"CREATED", "VERIFIED", "PUBLISHED", "REJECTED", "DELETED"})
    void shouldNotRequestPublicationNumberWhenPublishIsNotAllowed(RequestState state) {
        Request request = inState(state);
        AtomicBoolean numberRequested = new AtomicBoolean();

        assertThatThrownBy(() -> request.publish(() -> {
            numberRequested.set(true);
            return 1L;
        })).isInstanceOf(InvalidStateTransitionException.class);

        assertThat(numberRequested).isFalse();
        assertThat(request.getPublicationNumber()).isEqualTo(state == RequestState.PUBLISHED ? 1L : null);
    }

    @ParameterizedTest
    @EnumSource(value = RequestState.class, names = {"CREATED", "VERIFIED"})
    void shouldEditBodyInEditableStates(RequestState state) {
        Request request = inState(state);

        request.editBody("New content");

        assertThat(request.getBody()).isEqualTo("New content");
        assertThat(request.getState()).isEqualTo(state);
    }

    @ParameterizedTest
    @EnumSource(value = RequestState.class, names = {"ACCEPTED", "PUBLISHED", "REJECTED", "DELETED"})
    void shouldNotEditBodyInOtherStates(RequestState state) {
        Request request = inState(state);

        assertThatThrownBy(() -> request.editBody("New content"))
            .isInstanceOfSatisfying(EditNotAllowedException.class,
                ex -> assertThat(ex.getState()).isEqualTo(state));
        assertThat(request.getBody()).isEqualTo("Test-Body");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = "   ")
    void shouldNotEditBodyToBlank(String body) {
        Request request = Request.create("Test-Name", "Test-Body");

        assertThatThrownBy(() -> request.editBody(body))
            .isInstanceOf(MissingRequiredFieldException.class);
        assertThat(request.getBody()).isEqualTo("Test-Body");
    }

    @Test
    void shouldNotRecordHistoryOnBodyEdit() {
        Request request = Request.create("Test-Name", "Test-Body");

        request.editBody("New-Body");

        assertThat(request.getHistory()).isEmpty();
    }

    @Test
    void shouldNotExposeMutableHistory() {
        Request request = inState(RequestState.VERIFIED);

        assertThatThrownBy(() -> request.getHistory().clear())
            .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void shouldRestorePersistedState() {
        UUID id = UUID.randomUUID();
        Instant createdAt = Instant.parse("2026-01-01T10:00:00Z");
        Instant updatedAt = Instant.parse("2026-01-02T10:00:00Z");
        List<HistoryEntry> history = List.of(
            new HistoryEntry(RequestState.CREATED, RequestState.VERIFIED, RequestAction.VERIFY, null, updatedAt));

        Request request = Request.restore(
            id, "Test-Name", "Test-Body", RequestState.VERIFIED, null, createdAt, updatedAt, history);
        request.accept();

        assertThat(request.getId()).isEqualTo(id);
        assertThat(request.getCreatedAt()).isEqualTo(createdAt);
        assertThat(request.getState()).isEqualTo(RequestState.ACCEPTED);
        assertThat(request.getHistory()).hasSize(2).first().isEqualTo(history.getFirst());
    }

    private static Request inState(RequestState state) {
        Request request = Request.create("Test-Name", "Test-Body");
        switch (state) {
            case CREATED -> {
            }
            case VERIFIED -> request.verify();
            case ACCEPTED -> {
                request.verify();
                request.accept();
            }
            case PUBLISHED -> {
                request.verify();
                request.accept();
                request.publish(() -> 1L);
            }
            case REJECTED -> {
                request.verify();
                request.reject("reason");
            }
            case DELETED -> request.delete("reason");
        }
        return request;
    }
}
