package lk.ceylonpick.auth.repo;

import java.time.Instant;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import lk.ceylonpick.auth.domain.OtpChallenge;

public interface OtpChallengeRepository extends JpaRepository<OtpChallenge, String> {

    /**
     * FR-NOT-01 / IF-04: at most 3 OTPs per phone per hour. Counting rows rather
     * than holding a counter in memory keeps the limit correct across restarts
     * and across instances.
     */
    long countByPhoneAndCreatedAtAfter(String phone, Instant since);

    /**
     * FR-AUTH-04: was this user's step-up confirmed recently enough to still count
     * as fresh?
     */
    @Query("""
            select count(c) > 0 from OtpChallenge c
            where c.userId = :userId
              and c.purpose = :purpose
              and c.confirmedAt is not null
              and c.confirmedAt > :since
            """)
    boolean hasConfirmedSince(@Param("userId") String userId,
                              @Param("purpose") OtpChallenge.Purpose purpose,
                              @Param("since") Instant since);
}
