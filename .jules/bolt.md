## 2024-05-18 - Resolving N+1 query problem during Spring Data JPA pagination
**Learning:** To resolve N+1 query problems during Spring Data JPA pagination (`Page<T>`) for OneToMany relationships, using Hibernate's `@BatchSize` annotation on the collection rather than `@EntityGraph` avoids triggering inefficient in-memory pagination.
**Action:** Add `@BatchSize(size = 50)` (or an appropriate size) to the `@OneToMany` collection field in the parent entity.
