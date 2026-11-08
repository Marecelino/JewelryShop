## 2024-05-18 - Avoid @EntityGraph for Page<T> with Collections
**Learning:** Using `@EntityGraph` with Spring Data JPA pagination (`Page<T>`) on a OneToMany relationship triggers inefficient in-memory pagination. Hibernate logs a warning: 'firstResult/maxResults specified with collection fetch; applying in memory!'.
**Action:** Instead of `@EntityGraph`, use Hibernate's `@BatchSize` on the collection (e.g., `@BatchSize(size = 50)`) to avoid N+1 queries while allowing the database to handle pagination.
