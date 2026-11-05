## 2024-11-05 - Avoid @EntityGraph for Spring Data JPA Pagination with Collections
**Learning:** Using `@EntityGraph` on paginated Spring Data JPA queries (`Page<T>`) that fetch `OneToMany` collections causes Hibernate to fall back to inefficient in-memory pagination (fetching all records to memory and paginating there), because SQL pagination cannot safely be combined with collection joins.
**Action:** To resolve N+1 query problems for collections during pagination, use Hibernate's `@BatchSize` annotation on the collection property itself instead of using `@EntityGraph` on the query.
