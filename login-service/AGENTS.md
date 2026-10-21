# JewelryShop — Workspace Rules

## Project identity
This workspace is the JewelryShop backend project. It contains independent Maven/Spring Boot services rather than one Maven parent build. Confirm the current directory layout and each service's `pom.xml` before running commands or editing files.

Known service baseline from the project conversation (verify against current files before relying on it):
- `product-service`: port 8082; SQL Server database `JewelryStoreDemo`; package prefix previously used: `com.example.product`.
- `inventory-service`: port 8081; SQL Server database `JewelryInventoryDemo`; package prefix previously used: `com.example.inventory`.
- `login-service`: port 8083; SQL Server database `JewelryLoginDemo`; package prefix previously used: `com.example.login`.
- API Gateway is planned for port 8080; do not assume it exists until the workspace confirms it.
- Java 21 and Spring Boot 4.1.1 were previously reported; the actual `pom.xml` in the service being changed is authoritative.
- SQL Server is the database for JewelryShop services. Do not substitute PostgreSQL configuration from another project.

## Architecture boundaries
- Treat each service as independently built and configured unless the repository proves otherwise.
- Before making a change, identify the service and inspect its own `pom.xml`, application configuration, controller, service, repository, entities/DTOs, tests, and callers as relevant.
- Keep each service's data owned by its own database. Do not make one service read or write another service's database directly. Cross-service communication should use an explicitly agreed API contract.
- Do not create a root Maven aggregator, shared library, new service, or new package hierarchy unless explicitly requested.
- Do not assume endpoint paths, DTO fields, table names, database names, port numbers, or security rules from this file without checking current source.
- When changing an API contract, search for affected callers and explain whether product-service, inventory-service, login-service, or the planned gateway must also change.
- Preserve the existing architecture and avoid overengineering. Do not create interfaces plus implementation classes, generic response wrappers, mapper frameworks, or extra configuration merely because they are fashionable; follow established patterns in the target service.
## Strict Existing Structure Preservation

- The existing source code is the primary source of truth. Preserve the current project structure, package names, class names, endpoint paths, DTO contracts, and dependency relationships unless the user explicitly requests a change.
- Before coding, inspect the actual directory tree and trace the relevant request flow through Controller, Service, Repository, Entity, DTO, Mapper, configuration, and exception handling where applicable.
- Do not rename, move, delete, replace, or reorganize existing files or packages without explicit user approval.
- Do not introduce new layers, interfaces, implementation classes, design patterns, libraries, or shared modules unless there is a demonstrated requirement.
- Add a new class or file only when it is necessary for the requested functionality and consistent with the existing project conventions.
- Do not redesign an existing API, database schema, authentication mechanism, or service boundary merely to follow a preferred architecture.
- If the requested implementation conflicts with the existing structure, explain the conflict and propose the smallest compatible change before proceeding.
- Keep changes limited to the requested task. Do not mix unrelated refactoring into the implementation.
- Before finishing, inspect the diff and report any changed, added, deleted, or renamed files, explaining why each change was necessary.
## API and security principles
- Keep controllers focused on HTTP concerns; keep business rules in services; keep persistence logic in repositories.
- Use request/response DTOs at API boundaries. Do not serialize persistence entities directly unless the existing codebase has an explicit, deliberate design that requires it.
- Validate incoming data at the API boundary and preserve consistent error responses.
- Do not bypass authorization or service boundaries to make inter-service demos easier.
- For authentication changes, inspect the complete flow from database user to password verification, token generation, signature validation, authorization, and error handling.

## Database and local environment
- Inspect SQL scripts, entity mappings, column types, constraints, and application configuration before changing the schema.
- Preserve existing data by default. Never drop/truncate tables or reset the database without explicit permission.
- Do not print database passwords or copy local secrets into source files, rules, documentation, or reports.
- Keep service-specific configuration separate. Do not accidentally copy a port, database name, key path, or security setting from one service into another.

## Build and verification
- Since services may be separate Maven projects, do not assume running Maven at the workspace root builds every service.
- Discover the correct service directory and use its Maven Wrapper (`mvnw.cmd` on Windows) when available; otherwise use the installed Maven command.
- Run targeted tests/build for the service that changed. Run cross-service checks when an API contract or integration behavior changed.
- Report the exact command and whether it passed. If a test fails for an unrelated existing reason, give the actual failure rather than claiming success.
