package com.cadentia.catalog.repository;

import com.cadentia.catalog.entity.Song;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
public class InMemorySongRepository implements SongRepository {

    @Override
    public Optional<Song> findById(UUID id) {
        return Optional.empty();
    }
}
