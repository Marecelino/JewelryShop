
## 2024-05-14 - Optimizing OneToMany Pagination (Order -> OrderItem)
**Learning:** Using `@EntityGraph` on Spring Data JPA queries returning `Page<T>` for `OneToMany` relationships triggers extremely inefficient in-memory pagination (a `HHH000104: firstResult/maxResults specified with collection fetch; applying in memory!` warning).
**Action:** When resolving N+1 query problems during Spring Data JPA pagination for OneToMany relationships, use Hibernate's `@BatchSize` annotation on the collection rather than `@EntityGraph`.
