package pl.bzdon.requestmanagement.domain.query;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PageResultTest {

    @ParameterizedTest(name = "{0} elements / size {1} -> {2} pages")
    @CsvSource({
        "0,  10, 0",
        "1,  10, 1",
        "10, 10, 1",
        "11, 10, 2",
        "25, 10, 3"
    })
    void shouldCalculateTotalPages(long totalElements, int size, int expectedPages) {
        PageResult<String> result = new PageResult<>(List.of(), 0, size, totalElements);

        assertThat(result.totalPages()).isEqualTo(expectedPages);
    }

    @Test
    void shouldMapContentAndKeepPaging() {
        PageResult<Integer> result = new PageResult<>(List.of(1, 2), 1, 2, 5);

        PageResult<String> mapped = result.map(number -> "#" + number);

        assertThat(mapped.content()).containsExactly("#1", "#2");
        assertThat(mapped.page()).isEqualTo(1);
        assertThat(mapped.size()).isEqualTo(2);
        assertThat(mapped.totalElements()).isEqualTo(5);
    }

    @Test
    void shouldCopyContent() {
        List<String> content = new ArrayList<>(List.of("a"));
        PageResult<String> result = new PageResult<>(content, 0, 10, 1);

        content.add("b");

        assertThat(result.content()).containsExactly("a");
    }
}
