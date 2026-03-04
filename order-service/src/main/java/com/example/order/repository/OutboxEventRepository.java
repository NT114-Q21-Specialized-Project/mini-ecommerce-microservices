package com.example.order.repository;

import com.example.order.dto.PendingOutboxEventView;
import com.example.order.model.OutboxEvent;
import com.example.order.model.OutboxStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {
    List<OutboxEvent> findTop100ByStatusOrderByCreatedAtAsc(OutboxStatus status);

    @Query("""
        select oe
        from OutboxEvent oe
        where oe.status in :statuses
          and oe.nextAttemptAt <= :now
          and oe.retryCount < :maxRetryAttempts
        order by oe.createdAt asc
    """)
    List<OutboxEvent> findReadyForPublish(
            @Param("statuses") Collection<OutboxStatus> statuses,
            @Param("now") Instant now,
            @Param("maxRetryAttempts") int maxRetryAttempts,
            Pageable pageable
    );

    @Query("""
        select new com.example.order.dto.PendingOutboxEventView(
            oe.id,
            oe.aggregateType,
            oe.aggregateId,
            oe.eventType,
            oe.status,
            oe.createdAt,
            oe.publishedAt
        )
        from OutboxEvent oe
        where oe.status in :statuses
        order by oe.createdAt asc
    """)
    List<PendingOutboxEventView> findPendingViewByStatuses(@Param("statuses") Collection<OutboxStatus> statuses);
}
