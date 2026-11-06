
## 2023-10-24 - Spring Data JPA Pagination Pitfall
**Learning:** Using `@EntityGraph` to fetch `@OneToMany` collections on methods that use `Pageable` forces Hibernate to load all rows matching the root entity query into memory and paginate there (HHH000104 warning). This can cause severe memory issues on large datasets.
**Action:** Remove `@EntityGraph` from paginated queries. Instead, annotate the `@OneToMany` collection in the entity with `@org.hibernate.annotations.BatchSize(size = 100)`. This allows database-level pagination for the root entities while still avoiding N+1 queries when mapping elements later.
