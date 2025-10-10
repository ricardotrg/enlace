package com.gps.enlace.repo;

import com.gps.enlace.domain.MirrorLink;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.Optional;

public interface MirrorLinkRepo extends JpaRepository<MirrorLink, Long> {
    Optional<MirrorLink> findByToken(String token);
    long deleteByExpiresAtBefore(OffsetDateTime cutoff);
    @Query("""
  select m
  from MirrorLink m
  join fetch m.device d
  where m.token = :token
    and m.expiresAt > CURRENT_TIMESTAMP
""")
    Optional<MirrorLink> findActiveFetchDevice(@Param("token") String token);
}
