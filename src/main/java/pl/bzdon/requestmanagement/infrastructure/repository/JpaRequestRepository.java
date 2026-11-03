package pl.bzdon.requestmanagement.infrastructure.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import pl.bzdon.requestmanagement.infrastructure.entity.RequestEntity;

import java.util.UUID;

public interface JpaRequestRepository extends JpaRepository<RequestEntity, UUID>, JpaSpecificationExecutor<RequestEntity> {

    @Query(value = "SELECT nextval('publication_number_seq')", nativeQuery = true)
    long nextPublicationNumber();
}
