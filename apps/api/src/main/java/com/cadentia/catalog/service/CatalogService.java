package com.cadentia.catalog.service;

import com.cadentia.catalog.entity.Song;
import com.cadentia.catalog.repository.SongRepository;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class CatalogService {

    private final SongRepository songRepository;

    public CatalogService(SongRepository songRepository) {
        this.songRepository = songRepository;
    }

    public Optional<Song> findSong(UUID id) {
        return songRepository.findById(id);
    }
}
