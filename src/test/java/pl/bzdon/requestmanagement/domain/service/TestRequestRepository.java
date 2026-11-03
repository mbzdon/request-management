package pl.bzdon.requestmanagement.domain.service;

import pl.bzdon.requestmanagement.domain.query.PageQuery;
import pl.bzdon.requestmanagement.domain.query.PageResult;
import pl.bzdon.requestmanagement.domain.model.Request;
import pl.bzdon.requestmanagement.domain.query.RequestFilter;
import pl.bzdon.requestmanagement.domain.query.RequestSummary;
import pl.bzdon.requestmanagement.domain.repository.RequestRepository;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

class TestRequestRepository implements RequestRepository {

    private final Map<UUID, Request> requests = new HashMap<>();
    private long publicationNumber = 0;
    private int saveCount = 0;

    @Override
    public Request save(Request request) {
        saveCount++;
        requests.put(request.getId(), request);
        return request;
    }

    @Override
    public Optional<Request> findById(UUID id) {
        return Optional.ofNullable(requests.get(id));
    }

    @Override
    public PageResult<RequestSummary> findAll(RequestFilter filter, PageQuery pageQuery) {
        List<Request> matching = requests.values().stream()
            .filter(request -> !filter.hasName() || request.getName().toLowerCase().contains(filter.name().toLowerCase()))
            .filter(request -> !filter.hasState() || request.getState() == filter.state())
            .toList();
        List<RequestSummary> page = matching.stream()
            .skip((long) pageQuery.page() * pageQuery.size())
            .limit(pageQuery.size())
            .map(request -> new RequestSummary(request.getId(), request.getName(), request.getState(),
                request.getPublicationNumber(), request.getCreatedAt(), request.getUpdatedAt()))
            .toList();
        return new PageResult<>(page, pageQuery.page(), pageQuery.size(), matching.size());
    }

    @Override
    public long nextPublicationNumber() {
        return ++publicationNumber;
    }

    int saveCount() {
        return saveCount;
    }

    long issuedPublicationNumbers() {
        return publicationNumber;
    }
}
