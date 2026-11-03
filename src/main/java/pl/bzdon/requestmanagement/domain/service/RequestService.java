package pl.bzdon.requestmanagement.domain.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.bzdon.requestmanagement.domain.exception.RequestNotFoundException;
import pl.bzdon.requestmanagement.domain.model.HistoryEntry;
import pl.bzdon.requestmanagement.domain.query.PageQuery;
import pl.bzdon.requestmanagement.domain.query.PageResult;
import pl.bzdon.requestmanagement.domain.model.Request;
import pl.bzdon.requestmanagement.domain.model.statemachine.RequestAction;
import pl.bzdon.requestmanagement.domain.query.RequestFilter;
import pl.bzdon.requestmanagement.domain.query.RequestSummary;
import pl.bzdon.requestmanagement.domain.repository.RequestRepository;

import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class RequestService {

    private final RequestRepository requestRepository;

    public Request create(String name, String body) {
        Request created = requestRepository.save(Request.create(name, body));
        log.info("Request {} created", created.getId());
        return created;
    }

    public Request editBody(UUID id, String body) {
        Request edited = update(id, request -> request.editBody(body));
        log.info("Body of request {} edited", id);
        return edited;
    }

    public Request changeState(UUID id, RequestAction action, String reason) {
        Consumer<Request> operation = switch (action) {
            case VERIFY -> Request::verify;
            case ACCEPT -> Request::accept;
            case REJECT -> request -> request.reject(reason);
            case DELETE -> request -> request.delete(reason);
            case PUBLISH -> request -> request.publish(requestRepository::nextPublicationNumber);
        };
        Request changed = update(id, operation);
        log.info("Request {} moved to {} by {}", id, changed.getState(), action);

        return changed;
    }

    @Transactional(readOnly = true)
    public Request getById(UUID id) {
        return load(id);
    }

    @Transactional(readOnly = true)
    public List<HistoryEntry> getHistory(UUID id) {
        return load(id).getHistory();
    }

    @Transactional(readOnly = true)
    public PageResult<RequestSummary> findAll(RequestFilter filter, PageQuery pageQuery) {
        return requestRepository.findAll(filter, pageQuery);
    }

    private Request update(UUID id, Consumer<Request> operation) {
        Request request = load(id);
        operation.accept(request);
        return requestRepository.save(request);
    }

    private Request load(UUID id) {
        return requestRepository.findById(id).orElseThrow(() -> new RequestNotFoundException(id));
    }
}
