package com.example.order.repository;

import com.example.order.dto.PendingOutboxEventView;
import com.example.order.model.OutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {
    List<OutboxEvent> findTop100ByStatusOrderByCreatedAtAsc(String status);

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
        where oe.status = :status
        order by oe.createdAt asc
    """)
    List<PendingOutboxEventView> findPendingViewByStatus(@Param("status") String status);
}
