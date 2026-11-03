package pl.bzdon.requestmanagement.infrastructure.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import pl.bzdon.requestmanagement.domain.query.PageQuery;
import pl.bzdon.requestmanagement.domain.query.PageResult;
import pl.bzdon.requestmanagement.domain.model.Request;
import pl.bzdon.requestmanagement.domain.query.RequestFilter;
import pl.bzdon.requestmanagement.domain.query.RequestSummary;
import pl.bzdon.requestmanagement.domain.repository.RequestRepository;
import pl.bzdon.requestmanagement.infrastructure.entity.RequestEntity;
import pl.bzdon.requestmanagement.infrastructure.mapper.RequestMapper;

import java.util.Optional;
import java.util.UUID;

@Component
@Transactional
@RequiredArgsConstructor
public class PostgresRequestRepository implements RequestRepository {

    private static final Sort LISTING_ORDER = Sort.by(Sort.Order.desc("createdAt"), Sort.Order.asc("id"));

    private final JpaRequestRepository repository;

    @Override
    public Request save(Request request) {
        RequestEntity entity = repository.findById(request.getId()).orElseGet(() -> new RequestEntity(request.getId()));
        RequestMapper.copyToEntity(request, entity);
        return RequestMapper.toDomain(repository.save(entity));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Request> findById(UUID id) {
        return repository.findById(id).map(RequestMapper::toDomain);
    }

    @Override
    public long nextPublicationNumber() {
        return repository.nextPublicationNumber();
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<RequestSummary> findAll(RequestFilter filter, PageQuery pageQuery) {
        Page<RequestSummary> page = repository.findBy(RequestSpecifications.matching(filter), query -> query
            .as(RequestSummary.class)
            .page(PageRequest.of(pageQuery.page(), pageQuery.size(), LISTING_ORDER)));
        return new PageResult<>(page.getContent(), pageQuery.page(), pageQuery.size(), page.getTotalElements());
    }
}
