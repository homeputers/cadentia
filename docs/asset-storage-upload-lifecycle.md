# Asset storage and upload lifecycle

ADR-025 stores binary asset payloads outside the relational database and keeps
only durable metadata, checksums, lifecycle state, licensing, and access policy
in `asset_versions`. The API now uses a provider-neutral `AssetStorageAdapter`
so upload verification and asset-version creation do not depend on a specific
cloud provider.

## Local-development storage mode

Set `cadentia.asset-storage.provider=local` (the default) to use the local
filesystem adapter. Local mode writes objects below
`cadentia.asset-storage.local-root` and returns `local-asset://` signed URL
instructions. The URL is an instruction for local tooling and integration tests;
it does not expose a bucket name or credential. The storage key remains scoped
under the configured namespace and one of the lifecycle prefixes:

- `processing-prefix` for pending, not-yet-downloadable uploads.
- `available-prefix` for verified immutable asset versions.
- `quarantine-prefix` for rejected payloads retained for troubleshooting or
  malware-review workflows.

Operators can override these settings with environment variables, for example:

```yaml
cadentia:
  asset-storage:
    provider: local
    bucket: cadentia-local-assets
    namespace: local-development
    local-root: .cadentia/asset-storage
    signed-upload-url-ttl: PT15M
    signed-download-url-ttl: PT10M
    maximum-signed-url-ttl: PT1H
    pending-upload-ttl: PT2H
    maximum-object-size-bytes: 262144000
    processing-prefix: processing
    quarantine-prefix: quarantine
    available-prefix: assets
```

## Production S3 storage mode

Production or self-hosted deployments can use the built-in
`S3AssetStorageAdapter` by setting `cadentia.asset-storage.provider=s3` without
changing domain services or API controllers. The shared configuration carries the
common provider-neutral values that the local and S3 adapters need:

- Provider type (`provider`).
- Non-secret bucket/container alias (`bucket`).
- Instance namespace prefix (`namespace`).
- Secret-manager or KMS reference for encryption (`encryption-key-ref`), such as
  `aws-kms:alias/cadentia-assets-prod` or `sse-s3`.
- S3 region (`region`), optional endpoint override (`endpoint`), and path-style
  addressing flag (`path-style-access-enabled`) for self-hosted S3-compatible
  providers.
- Signed URL TTL caps.
- Maximum object size.
- Processing, quarantine, and available prefixes.
- Allowed MIME types by `AssetTypeCode`.

Raw credentials, session tokens, and signing secrets should remain in workload
identity or deployment secret configuration, not in normal API responses. The S3
adapter uses the AWS SDK default credential provider chain.

## Upload lifecycle

1. Cadentia creates a pending upload with the expected actor, instance, target
   asset, asset type, storage key, checksum, byte size, MIME type, access policy,
   and licensing metadata.
2. The storage adapter issues upload instructions for a processing-prefix key.
   No `asset_versions` row is created at this point, so the payload is not
   downloadable as a historical version.
3. The caller uploads the bytes to storage.
4. Finalization reads server-side object metadata through the adapter and
   verifies:
   - storage key equality;
   - checksum/digest;
   - byte size and configured maximum size;
   - MIME type and asset-type allowance;
   - actor and instance scope;
   - pending-upload expiration;
   - target asset type.
5. Only after verification succeeds does Cadentia move the object to the
   available prefix and create an `AVAILABLE` / `READY` immutable asset version.
6. Rejected, failed, or expired pending uploads remain outside the available
   prefix. Cleanup jobs may delete pending-upload objects, but must not delete
   immutable historical objects referenced by `asset_versions`.
