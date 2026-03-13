# ADR-001: Song Data Infrastructure and Storage Architecture

Status: Proposed  
Date: 2026-03-12

## Context

Cadentia is an AI-assisted system to help worship leaders build musically cohesive, doctrinally aligned setlists using a curated dataset and a deterministic recommendation engine.

The platform requires a structured data infrastructure capable of supporting:

- canonical song identity
- multiple arrangements per song
- lyrics and chord sheet versions
- tagging and thematic classification
- provenance tracking
- approval workflows
- efficient deterministic retrieval for recommendation
- future semantic enrichment

## Decision

Use **PostgreSQL as the system of record** with a normalized relational model centered on:

- songs
- arrangements
- lyrics_documents
- tags
- provenance_records
- approval_records
- import_batches

Optional semantic enrichment may later be supported through **pgvector**, but it will not replace deterministic recommendation logic.
