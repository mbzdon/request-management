package pl.bzdon.requestmanagement.domain.query;

import org.junit.jupiter.api.Test;
import pl.bzdon.requestmanagement.domain.model.statemachine.RequestState;

import static org.assertj.core.api.Assertions.assertThat;

class RequestFilterTest {

    @Test
    void shouldHaveNoCriteriaWhenEmpty() {
        assertThat(RequestFilter.none().hasName()).isFalse();
        assertThat(RequestFilter.none().hasState()).isFalse();
    }

    @Test
    void shouldIgnoreBlankName() {
        assertThat(new RequestFilter("  ", null).hasName()).isFalse();
    }

    @Test
    void shouldHaveCriteriaWhenSet() {
        RequestFilter filter = new RequestFilter("Test-Name", RequestState.CREATED);

        assertThat(filter.hasName()).isTrue();
        assertThat(filter.hasState()).isTrue();
    }
}
