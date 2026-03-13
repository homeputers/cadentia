# ADR-006: Arrangement Transposition Policy

Status: Proposed  
Date: 2026-03-13

## Context

Teams often need songs in different keys for vocalist range and set continuity.

## Decision

Store curated arrangements in a canonical base key. Generate transpositions dynamically through a transposition utility rather than persisting one arrangement per key by default.
