# ADR 0012 – REST and GraphQL as Parallel Delivery Transports

## Status
Accepted (inherited from template)

## Context

The template's stack carries both REST and GraphQL. The reference implementation it grew
from exposed REST only, and its class-role rules were written for one transport:
`*RestAPI` held the HTTP annotations, `*Controller` implemented it and held none.

Spring for GraphQL discovers resolvers by `@Controller`. With one transport that is
harmless; with two, a GraphQL resolver and a REST adapter both claim the `*Controller`
suffix — and `ClassRoleRulesTest` decides a class's role **by its name**. The gate would
have had to either ignore GraphQL resolvers or misclassify them.

## Decision

1. A use case MAY be exposed over one transport, both, or neither. The core does not know
   which.
2. Each transport contributes a **contract/adapter pair**: the interface carries every
   framework annotation, the implementing adapter carries none.

   | Transport | Contract | Adapter |
   |-----------|----------|---------|
   | REST | `*RestAPI` | `*RestController` |
   | GraphQL | `*GraphQLAPI` | `*GraphQLController` |

3. `*Controller` alone is no longer a role. The old `*Controller` classes were renamed.
4. Every delivery adapter obeys the same dependency rules — `core.inport` only, never a
   driver, never another transport's package.
5. Each transport owns its own DTOs and its own error translation. They are not shared.

## Rationale

**Why name the transport in the class name.** So the rule stays decidable. This is not
aesthetic: `ClassRoleRulesTest` reads names, and a role it cannot identify is a role it
cannot enforce.

**Why annotations on the interface, for GraphQL too.** Verified rather than assumed. Spring
MVC inherits `@RequestMapping` from an interface; Spring for GraphQL's documentation is
ambiguous — its detector calls `MethodIntrospector.selectMethods`, which does walk
interfaces, while a comment beside that code warns that `@SchemaMapping` must be on the
target class rather than a proxy interface. The pair was written with the annotations on the
interface and the slice test was allowed to decide. It passes.

**Why the example exposes one use case over both.** UC01 is reachable over REST and over
GraphQL, and `TourBookingGraphQLControllerTest.requestTourBooking_buildsTheSameCommandAsTheRestAdapter`
asserts that both adapters build the same command. That is the claim Ports & Adapters makes,
and it is the thing that silently stops being true first — usually when a second transport
arrives and a validation rule gets added to only one of them.

**Why not split by operation kind** (writes over REST, reads over GraphQL, which is how
GraphQL is usually used): it needs a read side, and the template has none by decision.
Recorded as a limitation rather than hidden.

## Consequences

- Two adapters, two DTO sets and two error translations per dually-exposed use case.
- **GraphQL's error vocabulary is coarser than HTTP's.** There is no `CONFLICT`, so a
  capacity clash and a malformed request both land on `BAD_REQUEST`, and an unreachable
  dependency lands on `INTERNAL_ERROR` rather than 502. § 9 of a use-case spec must state
  where two statuses collapse into one classification — a client author will hit it.
- **A GraphQL schema must declare a `Query` root**, even in a service with nothing to read.
  The example's root carries one infrastructure field, `apiVersion`, labelled in three
  places as not a domain read, because inventing a domain query to satisfy the parser would
  ship a read side no spec asked for.
- The `rest/` folder became `api/`, holding `.http` and `.graphql` request files. A folder
  named after one transport left the other's contract nowhere to live.
