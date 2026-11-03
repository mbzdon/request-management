package pl.bzdon.requestmanagement.domain.query;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PageQueryTest {

    @Test
    void shouldRejectNegativePage() {
        assertThatThrownBy(() -> new PageQuery(-1, 10)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldRejectNonPositiveSize() {
        assertThatThrownBy(() -> new PageQuery(0, 0)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldAcceptFirstPageOfOneElement() {
        assertThat(new PageQuery(0, 1)).isEqualTo(new PageQuery(0, 1));
    }
}
