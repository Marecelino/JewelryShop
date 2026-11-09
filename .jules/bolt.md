## 2026-11-09 - Resolving N+1 in Spring Data JPA Pagination
**Learning:** Using `@EntityGraph` for OneToMany relationships alongside `Page<T>` in Spring Data JPA triggers inefficient in-memory pagination (a `HHH000104: firstResult/maxResults specified with collection fetch; applying in memory!` warning).
**Action:** Use Hibernate's `@BatchSize` annotation on the collection field (e.g. `@BatchSize(size = 100)`) instead of `@EntityGraph` to resolve the N+1 problem efficiently when pagination is required.
