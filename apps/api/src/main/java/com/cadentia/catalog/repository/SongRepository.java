package com.cadentia.catalog.repository;

import com.cadentia.catalog.entity.Song;
import java.util.Optional;
import java.util.UUID;

public interface SongRepository {

    Optional<Song> findById(UUID id);
}
