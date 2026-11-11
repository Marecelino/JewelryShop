## 2024-05-24 - Resolving N+1 query problems in Spring Data JPA Pagination
**Learning:** For Spring Data JPA pagination (`Page<T>`) over OneToMany relationships, using Hibernate's `@BatchSize` annotation on the collection is preferable to using `@EntityGraph`. Using `@EntityGraph` forces in-memory pagination which is inefficient and generates warnings.
**Action:** Always use `@BatchSize` on collections when paginating the parent entity to resolve N+1 queries without triggering inefficient in-memory pagination.
