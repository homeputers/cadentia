# Cadentia OpenAPI contract layout

The API generator entrypoint remains `cadentia-api.yaml`. Keep this file focused on API metadata, tags, path indexes, and component indexes.

## Files

- `cadentia-api.yaml` — aggregate OpenAPI document consumed by tooling.
- `cadentia-api.paths.yaml` — compatibility index for all path items. New endpoint indexes should usually be added directly to `cadentia-api.yaml` and this file in the same shape.
- `cadentia-api.components.yaml` — aggregate component index for parameters, security schemes, schemas, and responses.
- `paths/*.yaml` — path-item definitions grouped by API area/tag.
- `components/schemas/*.yaml` — schema definitions grouped by owning domain.
- `components/shared/*.yaml` — reusable non-schema components such as parameters and standard responses. Security schemes stay inline in the aggregate files because the OpenAPI generator expects resolved auth metadata when processing operations.

## Editing guidelines

1. Add or update endpoint operations in the matching `paths/<api-area>.yaml` file.
2. Add domain-owned models in `components/schemas/<domain>.yaml`.
3. Add cross-cutting parameters and shared responses in `components/shared/`; keep security schemes inline in the aggregate indexes unless generator support changes.
4. Add corresponding `$ref` entries to the aggregate indexes so `cadentia-api.yaml` stays complete for generation.
5. Reference the owning split file directly from path and schema files; avoid routing nested `$ref` values back through `cadentia-api.components.yaml`, because that can make the OpenAPI generator emit duplicate `*1` model classes.
6. After changing the contract, run:

   ```bash
   mvn -pl apps/api -DskipTests generate-sources
   ```
