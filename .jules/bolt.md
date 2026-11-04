## 2024-05-24 - Spring Data JPA Pagination and OneToMany Relationships
**Learning:** Using `@EntityGraph` for OneToMany relationships with Spring Data JPA `Page<T>` triggers inefficient in-memory pagination.
**Action:** Use Hibernate's `@BatchSize` annotation on the collection rather than `@EntityGraph` to resolve N+1 query problems efficiently during Spring Data JPA pagination without triggering inefficient in-memory pagination.
