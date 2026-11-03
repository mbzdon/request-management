package pl.bzdon.requestmanagement.infrastructure.repository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import pl.bzdon.requestmanagement.IntegrationTest;
import pl.bzdon.requestmanagement.domain.model.HistoryEntry;
import pl.bzdon.requestmanagement.domain.query.PageQuery;
import pl.bzdon.requestmanagement.domain.query.PageResult;
import pl.bzdon.requestmanagement.domain.model.Request;
import pl.bzdon.requestmanagement.domain.model.statemachine.RequestAction;
import pl.bzdon.requestmanagement.domain.query.RequestFilter;
import pl.bzdon.requestmanagement.domain.query.RequestSummary;
import pl.bzdon.requestmanagement.domain.model.statemachine.RequestState;
import pl.bzdon.requestmanagement.domain.repository.RequestRepository;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.assertj.core.api.Assertions.within;

class PostgresRequestRepositoryTest extends IntegrationTest {

    private static final PageQuery FIRST_PAGE = new PageQuery(0, 10);

    @Autowired
    private RequestRepository repository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    void shouldSaveAndLoadRequest() {
        Request request = Request.create("Test-Name", "Test-Body");

        repository.save(request);

        Request loaded = repository.findById(request.getId()).orElseThrow();
        assertThat(loaded.getName()).isEqualTo("Test-Name");
        assertThat(loaded.getBody()).isEqualTo("Test-Body");
        assertThat(loaded.getState()).isEqualTo(RequestState.CREATED);
        assertThat(loaded.getPublicationNumber()).isNull();
        assertThat(loaded.getCreatedAt()).isCloseTo(request.getCreatedAt(), within(1, ChronoUnit.MILLIS));
        assertThat(loaded.getHistory()).isEmpty();
    }

    @Test
    void shouldReturnEmptyForUnknownId() {
        assertThat(repository.findById(UUID.randomUUID())).isEmpty();
    }

    @Test
    void shouldAppendHistoryAcrossSaves() {
        Request request = repository.save(Request.create("Test-Name", "Test-Body"));

        Request verified = repository.findById(request.getId()).orElseThrow();
        verified.verify();
        repository.save(verified);

        Request rejected = repository.findById(request.getId()).orElseThrow();
        rejected.reject("Budget exceeded");
        repository.save(rejected);

        Request loaded = repository.findById(request.getId()).orElseThrow();
        assertThat(loaded.getState()).isEqualTo(RequestState.REJECTED);
        assertThat(loaded.getHistory())
            .extracting(HistoryEntry::from, HistoryEntry::to, HistoryEntry::action, HistoryEntry::reason)
            .containsExactly(
                tuple(RequestState.CREATED, RequestState.VERIFIED, RequestAction.VERIFY, null),
                tuple(RequestState.VERIFIED, RequestState.REJECTED, RequestAction.REJECT, "Budget exceeded"));
    }

    @Test
    void shouldGenerateUniqueIncreasingPublicationNumbers() {
        long first = repository.nextPublicationNumber();
        long second = repository.nextPublicationNumber();

        assertThat(second).isGreaterThan(first);
    }

    @Test
    void shouldPersistPublicationNumber() {
        Request request = Request.create("Test-Name", "Test-Body");
        request.verify();
        request.accept();
        request.publish(() -> 4242L);

        repository.save(request);

        assertThat(repository.findById(request.getId()).orElseThrow().getPublicationNumber()).isEqualTo(4242L);
    }

    @Test
    void shouldRejectConcurrentModificationOfSameRequest() {
        UUID id = repository.save(Request.create("Test-Name", "Test-Body")).getId();
        TransactionTemplate outer = new TransactionTemplate(transactionManager);
        TransactionTemplate concurrent = new TransactionTemplate(transactionManager);
        concurrent.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        assertThatThrownBy(() -> outer.executeWithoutResult(status -> {
            Request mine = repository.findById(id).orElseThrow();

            concurrent.executeWithoutResult(inner -> {
                Request theirs = repository.findById(id).orElseThrow();
                theirs.delete("Duplicate");
                repository.save(theirs);
            });

            mine.verify();
            repository.save(mine);
        })).isInstanceOf(OptimisticLockingFailureException.class);

        assertThat(repository.findById(id).orElseThrow().getState()).isEqualTo(RequestState.DELETED);
    }

    @Test
    void shouldListSummariesWithoutBody() {
        Request request = Request.create("Test-Name", "Test-Body");
        request.verify();
        repository.save(request);

        RequestSummary summary = repository.findAll(RequestFilter.none(), FIRST_PAGE).content().getFirst();

        assertThat(summary.id()).isEqualTo(request.getId());
        assertThat(summary.name()).isEqualTo("Test-Name");
        assertThat(summary.state()).isEqualTo(RequestState.VERIFIED);
        assertThat(summary.publicationNumber()).isNull();
        assertThat(summary.createdAt()).isCloseTo(request.getCreatedAt(), within(1, ChronoUnit.MILLIS));
    }

    @Test
    void shouldFilterByNameCaseInsensitiveContains() {
        repository.save(Request.create("Holiday request", "Test-Body"));
        repository.save(Request.create("HOLIDAY", "Test-Body"));
        repository.save(Request.create("Laptop", "Test-Body"));

        PageResult<RequestSummary> result = repository.findAll(new RequestFilter("holiday", null), FIRST_PAGE);

        assertThat(result.content()).extracting(RequestSummary::name)
            .containsExactlyInAnyOrder("Holiday request", "HOLIDAY");
    }

    @Test
    void shouldTreatLikeWildcardsInNameLiterally() {
        repository.save(Request.create("100% refund", "Test-Body"));
        repository.save(Request.create("1000 refunds", "Test-Body"));
        repository.save(Request.create("a_b", "Test-Body"));
        repository.save(Request.create("axb", "Test-Body"));

        assertThat(repository.findAll(new RequestFilter("100%", null), FIRST_PAGE).content())
            .extracting(RequestSummary::name).containsExactly("100% refund");
        assertThat(repository.findAll(new RequestFilter("a_b", null), FIRST_PAGE).content())
            .extracting(RequestSummary::name).containsExactly("a_b");
        assertThat(repository.findAll(new RequestFilter("%", null), FIRST_PAGE).content())
            .extracting(RequestSummary::name).containsExactly("100% refund");
    }

    @Test
    void shouldFilterByState() {
        Request verified = Request.create("Verified", "Test-Body");
        verified.verify();
        repository.save(verified);
        repository.save(Request.create("Created", "Test-Body"));

        PageResult<RequestSummary> result = repository.findAll(new RequestFilter(null, RequestState.VERIFIED), FIRST_PAGE);

        assertThat(result.content()).extracting(RequestSummary::name).containsExactly("Verified");
    }

    @Test
    void shouldCombineNameAndStateFilters() {
        Request verifiedHoliday = Request.create("Holiday", "Test-Body");
        verifiedHoliday.verify();
        repository.save(verifiedHoliday);
        repository.save(Request.create("Holiday 2", "Test-Body"));
        Request verifiedLaptop = Request.create("Laptop", "Test-Body");
        verifiedLaptop.verify();
        repository.save(verifiedLaptop);

        PageResult<RequestSummary> result = repository.findAll(new RequestFilter("holiday", RequestState.VERIFIED), FIRST_PAGE);

        assertThat(result.content()).extracting(RequestSummary::name).containsExactly("Holiday");
    }

    @Test
    void shouldPaginateNewestFirst() {
        Instant base = Instant.parse("2026-01-01T10:00:00Z");
        for (int i = 1; i <= 5; i++) {
            Instant createdAt = base.plus(i, ChronoUnit.MINUTES);
            repository.save(Request.restore(UUID.randomUUID(), "Request " + i, "Test-Body", RequestState.CREATED, null,
                createdAt, createdAt, List.of()));
        }

        PageResult<RequestSummary> secondPage = repository.findAll(RequestFilter.none(), new PageQuery(1, 2));

        assertThat(secondPage.content()).extracting(RequestSummary::name).containsExactly("Request 3", "Request 2");
        assertThat(secondPage.totalElements()).isEqualTo(5);
        assertThat(secondPage.totalPages()).isEqualTo(3);
    }
}
