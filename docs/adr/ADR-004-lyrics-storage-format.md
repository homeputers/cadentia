# ADR-004: Lyrics Storage Format and Parsing Strategy

Status: Proposed  
Date: 2026-03-13

## Context

Lyrics and chord sheets may exist in plain text, ChordPro, OnSong, Markdown, or imported variants.

## Decision

Store raw source content plus declared format.

Supported formats:
- plain_text
- chordpro
- onsong
- markdown

Optional derived structures may later include:
- parsed sections JSON
- chord map
- structural markers
