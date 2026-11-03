package pl.bzdon.requestmanagement.infrastructure.entity;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.BatchSize;
import pl.bzdon.requestmanagement.domain.model.statemachine.RequestState;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Getter
@Setter
@Table(name = "requests")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RequestEntity {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, columnDefinition = "text")
    private String body;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RequestState state;

    @Column(unique = true)
    private Long publicationNumber;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    /**
     * Optimistic locking: two concurrent transitions of the same request cannot both succeed.
     */
    @Version
    private Long version;

    /**
     * Append-only audit log. The order column lets Hibernate insert only new entries instead of rewriting the list.
     */
    @ElementCollection
    @CollectionTable(name = "request_history", joinColumns = @JoinColumn(name = "request_id"))
    @OrderColumn(name = "entry_index")
    @BatchSize(size = 50)
    @Setter(AccessLevel.NONE)
    private List<HistoryEntryEmbeddable> history = new ArrayList<>();

    public RequestEntity(UUID id) {
        this.id = id;
    }
}
