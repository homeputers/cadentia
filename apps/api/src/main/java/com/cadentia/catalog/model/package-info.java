/**
 * Internal catalog write commands and enum-backed value types used by repository and service code.
 *
 * <p>These are not HTTP request or response DTOs. Public API contracts continue to come from the
 * OpenAPI specification and generated {@code com.cadentia.generated.*} classes. Catalog commands
 * represent application-side write intent after any controller mapping has already happened, which
 * keeps database validation separate from transport contracts.
 */
package com.cadentia.catalog.model;
