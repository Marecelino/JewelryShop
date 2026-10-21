---
trigger: glob
globs: "**/*.java, **/pom.xml"
description: "Java and Spring Boot implementation rules for the JewelryShop backend services."
---

# Java and Spring Boot Rules

## Compatibility first
- Read the target service's existing `pom.xml` and Java source before choosing an API or dependency. Keep the existing Java and Spring Boot versions; do not upgrade versions as part of an unrelated task.
- Prefer APIs compatible with the actual versions in this project. Do not copy examples for an older Spring Boot release without checking compatibility.
- Follow the target service's existing package naming, class naming, and layering conventions.

## Layering and dependency injection
- Keep HTTP parsing/status codes in controllers, business rules in services, and database queries in repositories.
- Use constructor injection. Do not introduce field injection with `@Autowired`.
- Keep methods focused. Extract a helper only when it improves readability or avoids real duplication.
- Follow the existing service pattern. Do not add `ServiceImpl` interfaces/implementations if the project does not already use that pattern and there is no concrete need.
- Do not access the database directly from controllers or call another service's repository to bypass service boundaries.

## DTOs, validation, and mapping
- Use dedicated request and response DTOs for public API contracts where that is the current project convention.
- Do not return a JPA entity from a controller by default; avoid exposing password hashes, internal flags, lazy-loaded relationships, or persistence implementation details.
- Validate external input with Jakarta Bean Validation annotations and `@Valid` where applicable. Put business-specific checks in the service, rather than duplicating all validation in controllers.
- Follow the project's existing mapper approach. Do not add MapStruct or another mapper dependency just to map a few fields.
- Keep response DTOs explicit and stable. API response changes require checking Swagger/OpenAPI documentation and callers.

## Transactions and persistence
- Put `@Transactional` at the service use-case boundary when a use case needs a transaction. Use `readOnly = true` for read-only transactions when consistent with the current code.
- Do not add transactions indiscriminately to every method or assume an annotation applies through self-invocation; understand Spring proxy behavior where relevant.
- Respect SQL Server-compatible mappings and the target database's current schema.
- Handle uniqueness and concurrent writes with database constraints as the final protection; a preceding `existsBy...` check alone is not race-safe.

## Exceptions and logging
- Use the project's centralized exception-handling strategy, such as an existing `@RestControllerAdvice`, rather than returning ad-hoc error objects from each controller.
- Do not use broad catches that silently convert every failure into a duplicate/conflict or success response.
- Do not swallow exceptions, return `null` for errors, or expose stack traces/SQL details to clients.
- Log useful diagnostic context without logging passwords, password hashes, bearer tokens, private key contents, or other secrets.

## Simplicity and maintainability
- No unrelated refactoring, speculative abstraction, or large method/class renaming as a side effect.
- Avoid magic strings and numbers when a named constant or existing enum is clearer; do not create constants for every trivial one-time value.
- Do not add Lombok or other dependencies unless the target service already uses them or the change has a clear benefit.
