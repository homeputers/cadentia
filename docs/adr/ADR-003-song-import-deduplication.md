# ADR-003: Song Import and Deduplication Workflow

Status: Proposed  
Date: 2026-03-13

## Context

Songs may be imported from multiple sources. Duplicate songs are common because of title variations, formatting differences, language differences, and partial metadata.

## Decision

Use a staged import pipeline:

1. import batch created
2. raw candidates stored
3. deduplication heuristics applied
4. admin review performed
5. merge into canonical song or create new entry

Deduplication signals:
- normalized title
- artist similarity
- CCLI number
- lyrics hash
- manual reviewer confirmation
