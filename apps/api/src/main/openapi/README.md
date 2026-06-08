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
3. Add cross-cutting parameters, auth schemes, and shared responses in `components/shared/`.
4. Add corresponding `$ref` entries to the aggregate indexes so `cadentia-api.yaml` stays complete for generation.
5. Use references through `cadentia-api.components.yaml` from split path/schema files to keep cross-file dependencies stable.
6. After changing the contract, run:

   ```bash
   mvn -pl apps/api -DskipTests generate-sources
   ```
