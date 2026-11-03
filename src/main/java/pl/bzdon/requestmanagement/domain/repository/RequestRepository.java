package pl.bzdon.requestmanagement.domain.repository;

import pl.bzdon.requestmanagement.domain.query.PageQuery;
import pl.bzdon.requestmanagement.domain.query.PageResult;
import pl.bzdon.requestmanagement.domain.model.Request;
import pl.bzdon.requestmanagement.domain.query.RequestFilter;
import pl.bzdon.requestmanagement.domain.query.RequestSummary;

import java.util.Optional;
import java.util.UUID;

public interface RequestRepository {

    Request save(Request request);

    Optional<Request> findById(UUID id);

    PageResult<RequestSummary> findAll(RequestFilter filter, PageQuery pageQuery);

    long nextPublicationNumber();
}
