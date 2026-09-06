package lk.ceylonpick.catalog.repo;

import java.time.Instant;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import lk.ceylonpick.catalog.domain.StockReservation;

public interface StockReservationRepository extends JpaRepository<StockReservation, String> {

    List<StockReservation> findByOrderIdAndReleasedAtIsNullAndConsumedAtIsNull(String orderId);

    /** What the sweep job collects: held past its deadline, neither released nor sold. */
    @Query("""
            select r from StockReservation r
             where r.expiresAt <= :now and r.releasedAt is null and r.consumedAt is null
            """)
    List<StockReservation> findExpired(@Param("now") Instant now);
}
