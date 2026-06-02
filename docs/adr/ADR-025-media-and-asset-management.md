# ADR-025: Media and Asset Management

Status: Proposed  
Date: 2026-05-28

## Context

Songs and arrangements often require charts, stems, backing tracks, click tracks, MIDI cues, rehearsal recordings, and related licensing metadata. These files can be large, versioned, and permission-sensitive.

## Problem

Storing media as incidental file links risks broken references, missing licensing information, unauthorized access, and unclear version history. Asset handling also needs to scale separately from relational catalog metadata.

## Decision

Introduce a permission-aware asset management model backed by cloud/object storage. Catalog entities, arrangements, services, and rehearsals may attach typed assets through metadata records that track ownership, version, storage location, checksum, licensing status, and access policy.

## Requirements

- Support PDFs, chord charts, stems, backing tracks, click tracks, MIDI cues, rehearsal recordings, and future asset types.
- Store binary payloads in object storage or equivalent durable blob storage.
- Store asset metadata in Cadentia with stable identifiers, checksums, MIME type, size, and storage key.
- Support asset versioning and immutable historical references.
- Attach assets to songs, arrangements, services, rehearsal sessions, and service-specific overrides.
- Enforce instance, role, and licensing permissions before download or streaming.
- Track licensing metadata, usage restrictions, source, and expiration where applicable.
- Support asynchronous processing for previews, waveform analysis, virus scanning, and transcoding.

## Acceptance Criteria

- Songs and arrangements can attach typed assets.
- Asset versions are distinguishable and auditable.
- Download/access decisions are permission-aware and instance-scoped.
- Licensing metadata is preserved and visible to authorized users.
- Recommendations never treat asset presence as approval unless catalog approval gates also pass.

## Consequences

Positive:

- Media workflows can scale independently from catalog rows.
- Licensing and access decisions become auditable.
- Services can reference exact asset versions used for rehearsal or performance.

Tradeoffs:

- Object storage lifecycle policies and cleanup jobs are required.
- Asset processing introduces async failure modes.
- Permission checks must be enforced at signed URL generation and metadata APIs.

## Alternatives Considered

1. Store files directly in the relational database.
   - Rejected: poor fit for large media and streaming access.
2. Store only external links.
   - Rejected: weak access control, versioning, and durability.
3. Treat assets as catalog approval evidence only.
   - Rejected: assets have independent lifecycle, licensing, and service-use concerns.

## Open Questions

- Which object storage provider is the production baseline?
- What asset retention policy applies after instance deletion or service completion?
- Which licensing fields are mandatory by asset type?
