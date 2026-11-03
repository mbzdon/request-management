package pl.bzdon.requestmanagement.endpoint.pagination;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PaginationPropertiesTest {

    private final PaginationProperties properties = new PaginationProperties(10, 100);

    @Test
    void shouldUseDefaultSizeWhenNotRequested() {
        assertThat(properties.resolve(null)).isEqualTo(10);
    }

    @Test
    void shouldUseRequestedSizeWithinLimit() {
        assertThat(properties.resolve(25)).isEqualTo(25);
        assertThat(properties.resolve(100)).isEqualTo(100);
    }

    @Test
    void shouldCapRequestedSizeAtMaximum() {
        assertThat(properties.resolve(1000)).isEqualTo(100);
    }

    @Test
    void shouldRejectInconsistentConfiguration() {
        assertThatThrownBy(() -> new PaginationProperties(0, 100)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PaginationProperties(50, 10)).isInstanceOf(IllegalArgumentException.class);
    }
}
