# Data access with jOOQ, generated from the migrated schema

The backend reaches PostgreSQL through jOOQ (Open Source Edition), with its classes generated on every build from the Flyway migrations applied to a real PostgreSQL container. The persistence adapters map jOOQ records into the plain-Java domain types. We chose it because it extends to the database the check ADR-0002 gives the API: when a query no longer matches the schema, the build fails instead of a test or a user finding out. The queries also stay explicit SQL that can be audited in a diff, the domain stays free of annotations, and it supports the PostgreSQL features Bookings will need, such as `tstzrange`.

We considered three alternatives:

- **`JdbcClient`**: the simplest, and a close second. But a SQL or mapping mistake only shows up when a test runs that query.
- **Spring Data JDBC**: it needs a Spring or jMolecules annotation to mark the id, which the domain rule forbids, so the domain would need a second model.
- **JPA/Hibernate**: much of its behaviour does not show in a diff (dirty checking, lazy loading, open-session-in-view), its entities cannot be records, and it has no native support for range types.

## Consequences

- Building the backend needs Docker, as its tests already do. jOOQ cannot generate from the migration files alone, because it cannot parse this schema's functional unique index and exclusion constraints.
- The generated code is not committed. Like the code generated from the Contract, it is rebuilt every time, so it cannot drift from the migrations.
- The Open Source Edition only supports the latest PostgreSQL version at each jOOQ release. We use the version Spring Boot manages and upgrade it together with Spring Boot.
- Mapping a constraint violation to its Problem type (ADR-0003) uses the constraint's name, written by hand, because jOOQ does not generate a constant for a functional index or an exclusion constraint.
- Semantic mistakes, such as a wrong join or a missing `WHERE`, still compile, so integration tests against PostgreSQL remain the persistence seam.
