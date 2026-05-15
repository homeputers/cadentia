package com.cadentia.catalog.entity;

import java.util.UUID;

public record Song(UUID id, String title, String language) {
}
