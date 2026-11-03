## 2026-11-03 - Fixing N+1 Queries in Spring Data JPA Pagination
**Learning:** Using `@EntityGraph` or `JOIN FETCH` for pagination (`Page<T>`) of `OneToMany` collections in Spring Data JPA triggers inefficient in-memory pagination because the database cannot safely limit rows when the result set cardinality is altered by the join.
**Action:** Always use Hibernate's `@BatchSize` annotation on the `@OneToMany` collection instead of `@EntityGraph` for paginated endpoints to avoid in-memory pagination while resolving N+1 queries.
