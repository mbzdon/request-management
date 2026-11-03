package pl.bzdon.requestmanagement.infrastructure.repository;

import org.springframework.data.jpa.domain.Specification;
import pl.bzdon.requestmanagement.domain.query.RequestFilter;
import pl.bzdon.requestmanagement.domain.model.statemachine.RequestState;
import pl.bzdon.requestmanagement.infrastructure.entity.RequestEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class RequestSpecifications {

    private static final char ESCAPE = '\\';

    private RequestSpecifications() {
    }

    static Specification<RequestEntity> matching(RequestFilter filter) {
        List<Specification<RequestEntity>> specs = new ArrayList<>();
        if (filter.hasName()) {
            specs.add(nameContains(filter.name()));
        }
        if (filter.hasState()) {
            specs.add(hasState(filter.state()));
        }
        return Specification.allOf(specs);
    }

    private static Specification<RequestEntity> nameContains(String name) {
        String pattern = "%" + escapeLike(name.trim().toLowerCase(Locale.ROOT)) + "%";
        return (root, query, cb) -> cb.like(cb.lower(root.get("name")), pattern, ESCAPE);
    }

    private static Specification<RequestEntity> hasState(RequestState state) {
        return (root, query, cb) -> cb.equal(root.get("state"), state);
    }

    private static String escapeLike(String value) {
        return value
            .replace(String.valueOf(ESCAPE), "" + ESCAPE + ESCAPE)
            .replace("%", ESCAPE + "%")
            .replace("_", ESCAPE + "_");
    }
}
