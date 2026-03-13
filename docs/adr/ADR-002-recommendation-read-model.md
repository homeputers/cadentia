# ADR-002: Recommendation Candidate Read Model Design

Status: Proposed  
Date: 2026-03-13

## Context

The Recommendation Engine must retrieve candidate songs efficiently using deterministic filters such as key compatibility, tempo ranges, tags, energy, language, and approval state.

## Decision

Introduce a read model optimized for candidate retrieval.

Initial implementation:
- SQL view: `v_recommendable_arrangements`

Later optimization:
- materialized view refreshed on a controlled cadence

The read model should expose:
- arrangement identity
- canonical song identity
- language
- key
- bpm
- time signature
- energy
- aggregated tags
- approval flags
