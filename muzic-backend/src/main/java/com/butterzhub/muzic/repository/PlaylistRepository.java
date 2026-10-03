package com.butterzhub.muzic.repository;

import com.butterzhub.muzic.model.Playlist;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PlaylistRepository extends JpaRepository<Playlist, UUID> {
    List<Playlist> findByUserId(UUID userId);
    List<Playlist> findTop10ByUserIdOrderByCreatedAtDesc(UUID userId);
    // Serialize sync attempts for a playlist and enforce ownership in the same query.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Playlist> findByIdAndUserId(UUID id, UUID userId);
}
