package pl.bzdon.requestmanagement.domain.model.statemachine;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import pl.bzdon.requestmanagement.domain.exception.InvalidStateTransitionException;

import java.util.Arrays;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RequestStateMachineTest {

    private static final Map<RequestState, Map<RequestAction, RequestState>> DIAGRAM = Map.of(
        RequestState.CREATED, Map.of(
            RequestAction.VERIFY, RequestState.VERIFIED,
            RequestAction.DELETE, RequestState.DELETED),
        RequestState.VERIFIED, Map.of(
            RequestAction.ACCEPT, RequestState.ACCEPTED,
            RequestAction.REJECT, RequestState.REJECTED),
        RequestState.ACCEPTED, Map.of(
            RequestAction.PUBLISH, RequestState.PUBLISHED,
            RequestAction.REJECT, RequestState.REJECTED)
    );

    private final RequestStateMachine stateMachine = new RequestStateMachine();

    @ParameterizedTest(name = "{0} --{1}--> {2}")
    @CsvSource({
        "CREATED,  VERIFY,  VERIFIED",
        "CREATED,  DELETE,  DELETED",
        "VERIFIED, ACCEPT,  ACCEPTED",
        "VERIFIED, REJECT,  REJECTED",
        "ACCEPTED, PUBLISH, PUBLISHED",
        "ACCEPTED, REJECT,  REJECTED"
    })
    void shouldAllowTransitionFromDiagram(RequestState from, RequestAction action, RequestState expected) {
        assertThat(stateMachine.next(from, action)).isEqualTo(expected);
    }

    @ParameterizedTest(name = "{0} --{1}--> forbidden")
    @MethodSource("forbiddenTransitions")
    void shouldRejectTransitionOutsideDiagram(RequestState from, RequestAction action) {
        assertThatThrownBy(() -> stateMachine.next(from, action))
            .isInstanceOf(InvalidStateTransitionException.class)
            .hasMessageContaining(from.name())
            .hasMessageContaining(action.name());
    }

    @ParameterizedTest
    @EnumSource(value = RequestState.class, names = {"PUBLISHED", "REJECTED", "DELETED"})
    void shouldNotAllowAnyActionFromFinalState(RequestState state) {
        for (RequestAction action : RequestAction.values()) {
            assertThatThrownBy(() -> stateMachine.next(state, action))
                .isInstanceOf(InvalidStateTransitionException.class);
        }
    }

    @Test
    void shouldAllowContentEditOnlyInCreatedAndVerified() {
        assertThat(Arrays.stream(RequestState.values()).filter(RequestState::isContentEditable))
            .containsExactlyInAnyOrder(RequestState.CREATED, RequestState.VERIFIED);
    }

    @Test
    void shouldRequireReasonOnlyForRejectAndDelete() {
        assertThat(Arrays.stream(RequestAction.values()).filter(RequestAction::requiresReason))
            .containsExactlyInAnyOrder(RequestAction.REJECT, RequestAction.DELETE);
    }

    private static Stream<Arguments> forbiddenTransitions() {
        return Arrays.stream(RequestState.values())
            .flatMap(state -> Arrays.stream(RequestAction.values())
                .filter(action -> !DIAGRAM.getOrDefault(state, Map.of()).containsKey(action))
                .map(action -> Arguments.of(state, action)));
    }
}
