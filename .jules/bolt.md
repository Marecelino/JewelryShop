## 2024-03-24 - N+1 Query in Pagination
**Learning:** To resolve N+1 query problems during Spring Data JPA pagination (`Page<T>`) for OneToMany relationships, use Hibernate's `@BatchSize` annotation on the collection rather than `@EntityGraph` to avoid triggering inefficient in-memory pagination.
**Action:** Use `@BatchSize(size = 50)` on `@OneToMany` relationships and avoid `@EntityGraph` for paginated queries in Spring Data JPA.
