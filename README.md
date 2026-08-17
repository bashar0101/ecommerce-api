# E-Commerce API

A learning project: a REST API for a small online store, built with Spring Boot.

Users register, confirm their address through an emailed activation link, log in for a JWT, browse a product catalog, and place orders. Each order snapshots the product price at purchase time, and stock is decremented inside the same transaction that writes the order.

This README is written to be read top to bottom by someone who has never seen the codebase. It explains every class, what each method does, and how the pieces connect.

---

## Contents

- [Status](#status)
- [Tech stack](#tech-stack)
- [Running it](#running-it)
- [Architecture](#architecture)
- [Package map](#package-map)
- [Class reference](#class-reference)
  - [entity](#entity--the-database-shape)
  - [enums](#enums)
  - [repository](#repository--database-access)
  - [dto](#dto--what-the-api-speaks)
  - [service](#service--the-business-logic)
  - [security](#security--who-are-you)
  - [config](#config--wiring)
  - [controller](#controller--http)
  - [exception](#exception--turning-throwables-into-json)
- [How a feature works, end to end](#how-a-feature-works-end-to-end)
  - [Registration and email verification](#1-registration-and-email-verification)
  - [Login](#2-login)
  - [An authenticated request](#3-an-authenticated-request)
  - [Placing an order](#4-placing-an-order)
- [API reference](#api-reference)
- [Configuration](#configuration)
- [Testing](#testing)
- [Known issues and what's missing](#known-issues-and-whats-missing)

---

## Status

Working end to end:

- **Products** — full CRUD. Reads are public, writes are admin-only.
- **Registration with email verification** — new accounts start disabled and cannot log in until the emailed link is clicked.
- **Login** — returns a JWT valid for 24 hours.
- **Orders** — placed by the authenticated user, with a stock check and transactional rollback.

Not built yet: order status transitions (`PENDING → PAID → SHIPPED`), cancellation with stock restored, password reset, refresh tokens.

There are real defects in the current code. They are listed honestly in [Known issues](#known-issues-and-whats-missing) rather than hidden.

## Tech stack

| Piece | Choice |
|---|---|
| Language | Java 17 |
| Framework | Spring Boot 4.1.0 (Web MVC, Data JPA, Security, Validation, Mail) |
| Database | MySQL 8 |
| Auth | JWT via `io.jsonwebtoken` (jjwt 0.12.6) |
| Mail (dev) | Mailpit — a fake SMTP server with a web inbox |
| Tests | JUnit 5, Mockito, Spring Test, H2 in-memory |
| Build | Maven (`./mvnw`) |
| Boilerplate | Lombok |

## Running it

**Prerequisites:** JDK 17+, MySQL on `localhost:3306`, and an SMTP server on `localhost:1025` for activation mail.

Create `.env` in the project root (gitignored — copy `.env.example`):

```properties
DB_USER=root
DB_PASSWORD=your_password
JWT_SECRET=a_long_random_string_at_least_32_bytes
```

`JWT_SECRET` must be at least 32 bytes — `Keys.hmacShaKeyFor` rejects anything shorter, and the app will not start.

Start Mailpit for the activation emails, then the app:

```bash
docker compose up -d mail        # SMTP on 1025, web inbox on http://localhost:8025
./mvnw spring-boot:run
```

Everything in Docker instead:

```bash
docker compose up --build
```

`compose.yaml` defines three services — `mail` (Mailpit), `db` (MySQL 8.4 on host port **3307**, with a persistent `mysql_data` volume), and `app`.

Read the activation emails at **http://localhost:8025**. Nothing leaves your machine.

## Architecture

Four layers, each allowed to talk only to the one below it:

```
HTTP request
    │
    ▼
┌─────────────────────────────────────────────────────┐
│ SecurityFilterChain                                 │
│   JwtAuthFilter — reads "Authorization: Bearer …",  │
│   populates the SecurityContext                     │
│   then the URL rules decide 401 / 403 / continue    │
└─────────────────────────────────────────────────────┘
    │
    ▼
┌─────────────────────────────────────────────────────┐
│ Controller — HTTP only. Validates the body, reads   │
│ the principal, returns status codes. No logic.      │
└─────────────────────────────────────────────────────┘
    │  DTOs in, DTOs out
    ▼
┌─────────────────────────────────────────────────────┐
│ Service — the rules. Transactions, stock checks,    │
│ password hashing, entity ↔ DTO mapping.             │
└─────────────────────────────────────────────────────┘
    │  entities
    ▼
┌─────────────────────────────────────────────────────┐
│ Repository — Spring Data JPA. Queries only.         │
└─────────────────────────────────────────────────────┘
    │
    ▼
  MySQL
```

Two rules hold this together:

1. **Entities never leave the service layer.** Controllers receive and return DTOs. This is why a `User` entity is never serialized to JSON — its `password` field would go with it.
2. **Controllers contain no business logic.** They translate HTTP into a service call and back.

Alongside that runs one **asynchronous** path: registration publishes an event, and a listener sends the activation email *after* the database transaction commits. Mail delivery is slow and can fail, so it must not sit inside the request.

## Package map

```
com.apps.ecommerce
├── EcommerceApplication      main class
├── config/                   SecurityConfig, UserRegisteredListener, DataSeeder
├── controller/               AuthController, ProductController, OrderController, UserController
├── dto/                      request/response records — the API's vocabulary
├── entity/                   User, Product, Order, OrderItem, VerificationToken
├── enums/                    Role, OrderStatus
├── exception/                custom exceptions + GlobalExceptionHandler
├── repository/               Spring Data JPA interfaces
├── security/                 JwtService, JwtAuthFilter, AppUserDetailsService
└── service/                  AuthService, ProductService, OrderService, MailService, UserService
```

---

## Class reference

### `EcommerceApplication`

The entry point. Three annotations, each earning its place:

- `@SpringBootApplication` — component scanning and auto-configuration.
- `@EnableAsync` — required for `@Async` on `UserRegisteredListener`. Without it the annotation is silently ignored and mail sends on the request thread.
- `@EnableSpringDataWebSupport(pageSerializationMode = VIA_DTO)` — serializes `Page` through a stable DTO instead of Spring's internal `PageImpl` shape, so paginated product JSON does not change if the framework does.

---

### entity — the database shape

JPA entities. One class per table, and the schema is generated from them.

#### `User` → table `users`

| Field | Column | Notes |
|---|---|---|
| `id` | UUID PK | `@GeneratedValue(strategy = UUID)` |
| `firstName`, `lastName` | not null | |
| `email` | not null, **unique** | also the login username |
| `password` | not null | BCrypt hash, never plaintext |
| `role` | not null, `@Enumerated(STRING)` | defaults to `Role.USER` |
| `enabled` | nullable | **false until the email is verified** |
| `createdAt` | not null | set by `AuthService` |

Named `users`, not `user`, because `USER` is a reserved word in standard SQL. MySQL tolerates it; H2 (used by the tests) does not.

`enabled` is the gate the whole verification feature hangs on. `AppUserDetailsService` translates it into Spring Security's "account disabled" state, which makes login fail for unverified users.

`equals` compares `id` only — for an entity, identity means "same row", not "same field values". `hashCode` returns a constant. That looks wrong but is deliberate: put an unsaved entity in a `HashSet`, let Hibernate assign the id on insert, and an id-derived hash would change, moving the object to a different bucket where `contains()` can no longer find it. A constant hash cannot drift. `ProductTest` pins this behaviour.

#### `Product`

`id`, `name`, `price` (`BigDecimal`), `stock` (`Integer`), `description` — all non-null. Same `equals`/`hashCode` reasoning as `User`, plus a `toString` for readable test failures.

#### `Order` → table `orders`

Named `orders` because `ORDER` is reserved SQL.

| Field | Notes |
|---|---|
| `user` | `@ManyToOne(fetch = LAZY, optional = false)` → `user_id` |
| `status` | `OrderStatus`, stored as a string |
| `totalPrice` | `BigDecimal`, **stored** not computed |
| `createdAt` | `@CreationTimestamp`, `updatable = false` |
| `items` | `@OneToMany(mappedBy = "order", cascade = ALL, orphanRemoval = true)` |

`cascade = ALL` is what lets `OrderService` save an order and its line items in a single `save()` call.

`totalPrice` is a real column rather than a computed value so that a later price change cannot rewrite what a customer was charged.

#### `OrderItem`

The join between an order and a product, carrying `quantity` and `unitPrice`. `unitPrice` is **copied from the product at purchase time** — same reasoning as `totalPrice`.

#### `VerificationToken`

The newest entity, added with the mail feature.

| Field | Notes |
|---|---|
| `id` | UUID PK |
| `token` | not null, **unique** — the random string in the emailed link |
| `user` | `@ManyToOne(LAZY, optional = false)` |
| `expiresAt` | not null — 24 hours after issue |
| `usedAt` | nullable — **null means unused**; set when the link is clicked |

Two nullable-vs-set fields encode the whole state machine: unused and unexpired = valid; `usedAt` set = already consumed; `expiresAt` in the past = too late.

---

### enums

- **`Role`** — `USER`, `ADMIN`. `AppUserDetailsService` turns these into Spring authorities `ROLE_USER` / `ROLE_ADMIN`.
- **`OrderStatus`** — `PENDING`, `PAID`, `SHIPPED`, `CANCELLED`. Only `PENDING` is ever set today.

---

### repository — database access

Spring Data JPA. You declare an interface; Spring generates the implementation at startup. Method names are parsed into queries, so `findByEmail` becomes `SELECT … WHERE email = ?` with no SQL written by hand.

| Interface | Extra methods | Used by |
|---|---|---|
| `UserRepository` | `findByEmail(String)` → `Optional<User>`, `existsByEmail(String)` → `boolean` | `AuthService`, `OrderService`, `AppUserDetailsService`, `OrderController` |
| `ProductRepository` | `findByName(String)` → `Optional<Product>` | `ProductService`, `OrderService` |
| `OrderRepository` | `findAllByUser(User)` → `List<Order>` | `OrderService` |
| `VerificationTokenRepository` | `findByToken(String)` → `Optional<VerificationToken>` | `AuthService` |
| `OrderItemRepository` | none | nothing — items are cascaded through `Order` |

All inherit `save`, `findById`, `findAll`, `delete`, `count` from `JpaRepository`.

`existsByEmail` matters: it asks the database a yes/no question instead of loading a whole `User` just to check presence.

---

### dto — what the API speaks

Java `record`s. Immutable, no boilerplate, and validated declaratively — the annotations are the contract.

**Requests**

| Record | Fields and rules |
|---|---|
| `UserCreateRequest` | `firstName`/`lastName` — `@NotBlank`, `@Size(2,50)`, letters-only pattern · `email` — `@NotBlank @Email` · `password` — `@NotBlank`, `@Size(8,72)`, `@Pattern` · `role` — `@NotNull Role` |
| `LoginRequest` | `email` — `@NotBlank @Email` · `password` — `@NotBlank` |
| `ResendVerificationRequest` | `email` — `@NotBlank @Email` |
| `ProductCreateRequest` | `name` — `@NotBlank` · `price` — `@NotNull @DecimalMin("0.0", inclusive=false)` · `stock` — `@NotNull @Min(0)` · `description` |
| `OrderCreateRequest` | `items` — `@NotEmpty @Valid List<OrderItemRequest>` |
| `OrderItemRequest` | `productId` — `@NotNull` · `quantity` — `@NotNull @Min(1)` |

The password rules are worth reading closely:

```java
@Size(min = 8, max = 72)
@Pattern(regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)\\S+$")
```

- `@NotBlank` alone accepts `"   +   "` — it only rejects strings that are *entirely* whitespace. `\S+` forbids whitespace anywhere.
- The three lookaheads each scan the whole string for one character class without consuming it, so all must hold at once.
- `max = 72` is BCrypt's limit. It silently ignores everything past 72 bytes, so accepting a longer password would be a lie.

The `@Valid` on `OrderCreateRequest.items` is what makes validation descend into the nested records. Without it only the list itself is checked, and a `quantity` of `0` would slip through to the service.

**Responses**

| Record | Fields |
|---|---|
| `UserCreateResponse` | `email`, `firstName`, `lastName`, `role`, `createdAt` — **no password, no id** |
| `ProductResponse` | `id`, `name`, `price`, `stock`, `description` |
| `OrderResponse` | `id`, `status`, `totalPrice`, `createdAt`, `List<OrderItemResponse>` |
| `OrderItemResponse` | `productId`, `productName`, `quantity`, `unitPrice` |

**Event**

`UserRegisteredEvent(UUID userId, String email, String token)` — the payload passed from `AuthService` to `UserRegisteredListener`.

---

### service — the business logic

#### `AuthService`

The heart of the auth feature. Depends on `UserRepository`, `PasswordEncoder`, `JwtService`, `AuthenticationManager`, `VerificationTokenRepository`, and `ApplicationEventPublisher`.

**`register(UserCreateRequest)` → `UserCreateResponse`** — `@Transactional`

1. `existsByEmail` → throw `DuplicateResourceException` (409) if taken.
2. Build the `User`: hash the password with `passwordEncoder.encode`, set `createdAt`, and **`setEnabled(false)`** — the account exists but cannot log in.
3. Save the user.
4. Mint a `VerificationToken`: a random `UUID` string, expiring in 24 hours.
5. `events.publishEvent(new UserRegisteredEvent(...))` — hand the email off, do not send it here.
6. Return a DTO with no password.

The publish is the seam between the fast path and the slow one. Everything above it is a database write inside one transaction; the email is somebody else's problem.

**`login(LoginRequest)` → `String`**

Delegates to `authManager.authenticate(...)`, which loads the user through `AppUserDetailsService`, compares the BCrypt hash, and **checks the enabled flag**. Wrong password throws `BadCredentialsException`; an unverified account throws `DisabledException`. Both are `AuthenticationException`, which the handler maps to 401. Only on success does it call `jwtService.generateToken`.

**`verify(String tokenValue)`** — `@Transactional`

1. `findByToken` → `InvalidTokenException` if unknown.
2. `usedAt != null` → "Link already used".
3. `expiresAt` in the past → "Link expired".
4. `user.setEnabled(true)` and `token.setUsedAt(now)`.

There is no `save()` call here, and that is correct: both objects were loaded inside the transaction, so they are *managed*. Hibernate's dirty checking writes the `UPDATE` statements at commit.

**`resendVerification(String email)`** — `@Transactional`

Finds the user, refuses if already enabled, mints a fresh token, publishes the same event.

#### `MailService`

`send(String to, String subject, String text)` — builds a `SimpleMailMessage` and hands it to Spring's `JavaMailSender`. One method, deliberately dumb, so the sending mechanism can be swapped without touching business code.

#### `OrderService`

**`create(String email, OrderCreateRequest)` → `OrderResponse`** — `@Transactional`

1. `findByEmail(email)` → 404 if unknown. The caller passes the **email from the JWT**, never a client-supplied id.
2. Collect the requested product ids into a `Set` — this deduplicates.
3. `productRepository.findAllById(ids)` — **one** query, then index into a `Map<UUID, Product>`. Calling `findById` inside the loop would be one query per line item (the N+1 problem).
4. If the map is smaller than the id set, some product does not exist → 404 naming it. Checked up front, before any stock is touched.
5. Build the order: attach the user, `status = PENDING`, total starts at `BigDecimal.ZERO`.
6. For each item — look the product up in the map (no query), **check stock**, decrement it, build an `OrderItem` with `unitPrice` copied from the product, link both directions, and add `unitPrice × quantity` to the total.
7. `orderRepository.save(order)` once — `cascade = ALL` persists the line items with it.
8. Map to a DTO.

Stock decrements need no explicit save. The `Product` objects are managed entities inside the transaction, so Hibernate emits the `UPDATE`s at commit. And because the whole method is one transaction, an out-of-stock item on line 3 rolls back the decrements from lines 1 and 2 — no partial orders, no leaked stock.

Money is `BigDecimal` throughout, never `double`: binary floating point cannot represent `0.1` exactly and totals drift. The total is computed on the server and never read from the request.

**`getAllByUserId(UUID)` → `List<OrderResponse>`** — loads the user, then `findAllByUser`, mapping each to a DTO.

**`toDto(Order)`** — private mapper, the only place `Order` becomes `OrderResponse`.

#### `ProductService`

Straightforward CRUD over `ProductRepository`:

| Method | Does |
|---|---|
| `create(ProductCreateRequest)` | build, save, return DTO |
| `findById(UUID)` | via `getEntity` |
| `findByName(String)` | 404 if absent |
| `findAll(Pageable)` | `Page<Product>` → `Page<ProductResponse>` via `.map` |
| `update(UUID, ProductCreateRequest)` | `@Transactional` — full replace |
| `deleteById(UUID)` | loads first so a missing id is 404, not a silent no-op |
| `getEntity(UUID)` *(private)* | the shared "find or 404" helper |
| `toDto(Product)` *(private)* | the single mapping point |

#### `UserService`

`existsByEmail` and `save`. Currently **unused** — `AuthService` talks to `UserRepository` directly. Left over from before `AuthService` existed.

---

### security — who are you

#### `JwtService`

Constructor-injected with `${jwt.secret}` and `${jwt.expiration-ms}`, turning the secret into a `SecretKey` via `Keys.hmacShaKeyFor`.

- **`generateToken(Optional<User>)`** — builds a signed JWT: subject = **email**, plus `userId` and `role` claims, issued now, expiring after `expirationMs` (24h).
- **`parse(String)` → `Claims`** — verifies the signature and expiry, throwing if either fails.

The subject is the email, which is why `OrderController` can pass `principal.getUsername()` straight to `OrderService.create`.

#### `AppUserDetailsService implements UserDetailsService`

**`loadUserByUsername(String email)`** — the bridge between your `User` entity and Spring Security's model. Loads by email (`UsernameNotFoundException` if absent) and returns a Spring `User` with the email, the BCrypt hash, `.roles(role.name())` → authority `ROLE_ADMIN`/`ROLE_USER`, and **`.disabled(!user.isEnabled())`**.

That last line is the entire enforcement of email verification on the login path.

#### `JwtAuthFilter extends OncePerRequestFilter`

Registered before `UsernamePasswordAuthenticationFilter`. On every request:

1. Read the `Authorization` header; if it is missing or not `Bearer …`, do nothing and continue.
2. `jwtService.parse(token)` → `Claims`.
3. `loadUserByUsername(claims.getSubject())`.
4. Put a `UsernamePasswordAuthenticationToken` into the `SecurityContext`.
5. Any exception → swallow it and stay anonymous, letting the URL rules produce a clean 401 instead of a stack trace.

`OncePerRequestFilter` guarantees this runs once per request even with forwards.

---

### config — wiring

#### `SecurityConfig`

```java
.csrf(disable)                                   // stateless API, no cookies to protect
.exceptionHandling(→ HttpStatusEntryPoint(401))  // unauthenticated ⇒ 401, not a login redirect
.sessionManagement(STATELESS)                    // no HttpSession; the JWT is the state
.authorizeHttpRequests(
    "/api/v1/auth/**"            → permitAll     // register, login, verify, resend
    GET "/api/v1/products/**"    → permitAll     // public catalog
    "/api/v1/products/**"        → hasRole(ADMIN) // POST/PUT/DELETE
    anyRequest                   → authenticated  // orders, everything else
)
.addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
```

Order matters: the `GET` rule must precede the blanket products rule, or reads would demand admin too.

Also declares two beans: **`PasswordEncoder`** (`BCryptPasswordEncoder`, used by `AuthService` to hash and by the auth manager to verify) and **`AuthenticationManager`** (pulled from `AuthenticationConfiguration`, wired to `AppUserDetailsService` + the encoder).

#### `UserRegisteredListener`

```java
@Async
@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
public void onUserRegistered(UserRegisteredEvent event)
```

Both annotations are load-bearing:

- **`AFTER_COMMIT`** — the email is only sent once the user and token are actually committed. A plain `@EventListener` would fire inside the transaction, and a later rollback would leave a live activation link for a user that does not exist.
- **`@Async`** — sending happens on a separate thread, so a slow or dead SMTP server does not delay the HTTP response. This requires `@EnableAsync` on the main class.

It builds the link as `${app.base-url}/api/v1/auth/verify?token=…` and catches every exception so a mail failure cannot escape into an async void.

#### `DataSeeder`

Currently **entirely commented out**. It used to insert a fixed test user at startup. Superseded by real registration.

---

### controller — HTTP

#### `AuthController` — `/api/v1/auth`

| Method | Endpoint | Does |
|---|---|---|
| `register` | `POST /register` | `@Valid` body → `authService.register` → **201** |
| `login` | `POST /login` | → `{"token": "..."}` → **200** |
| `verify` | `GET /verify?token=…` | → `{"message": "Account verified successfully"}` |
| `resend` | `POST /resend` | → `{"message": "Verification email sent"}` |
| `testMail` | `GET /test-mail` | **debug leftover — delete it**, see [Known issues](#known-issues-and-whats-missing) |

#### `ProductController` — `/api/v1/products`

`findAll` (paged, `@PageableDefault(size = 20, sort = "name")`), `findById`, `createProduct` (201 + `Location`), `update`, `delete` (204). Authorization is enforced entirely by `SecurityConfig`, not by annotations here.

#### `OrderController` — `/api/v1/orders`

- **`createOrder(@AuthenticationPrincipal UserDetails principal, @Valid @RequestBody OrderCreateRequest)`** — passes `principal.getUsername()` (the email from the JWT) to the service. The buyer's identity comes from the verified token, never from the request, so nobody can order as somebody else.
- **`getOrdersByUserId(@AuthenticationPrincipal UserDetails principal)`** — resolves the email to a UUID via `userRepository`, then `orderService.getAllByUserId`.

#### `UserController` — `/api/v1/users`

Its only endpoint is commented out. Registration lives in `AuthController` now. Vestigial.

---

### exception — turning throwables into JSON

| Exception | Meaning |
|---|---|
| `ResourceNotFoundException` | unknown id/email; has a `(Class, Object)` constructor for a uniform message |
| `DuplicateResourceException` | email already registered |
| `InsufficientStockException` | carries product name, available, requested |
| `InvalidTokenException` | bad, used, or expired activation link |

`ErrorResponse` is the single response shape:

```json
{
  "status": 400,
  "message": "Not valid",
  "error": "...",
  "timestamp": "2026-08-17T12:00:00",
  "fields": { "password": "Password must be between 8 and 72 characters" }
}
```

`fields` is populated only for validation failures.

`GlobalExceptionHandler` (`@RestControllerAdvice`) maps them:

| Trigger | Status |
|---|---|
| `MethodArgumentNotValidException` (a `@Valid` failure) | 400 + `fields` |
| `HttpMessageNotReadableException` (malformed JSON, bad enum value) | 400 |
| `InvalidTokenException` | 400 |
| `AuthenticationException` (bad password, disabled account) | 401 |
| `ResourceNotFoundException` | 404 |
| `DuplicateResourceException` | 409 |
| `InsufficientStockException` | 409 |
| anything else | 500, logged with a stack trace |

The `HttpMessageNotReadableException` handler exists because `@ExceptionHandler(Exception.class)` is consulted *before* Spring's default resolver. Without an explicit handler, `{"role": "superuser"}` — a client mistake — would be reported as a 500.

---

## How a feature works, end to end

### 1. Registration and email verification

```
POST /api/v1/auth/register
      │
      ▼
AuthController.register            @Valid runs first — a weak password never reaches the service
      │
      ▼
AuthService.register               ┌── @Transactional ──────────────────┐
      ├─ existsByEmail?            │  409 if taken                      │
      ├─ save User (enabled=false) │                                    │
      ├─ save VerificationToken    │  random UUID, expires in 24h       │
      └─ publishEvent(...)         │  queued, not sent                  │
                                   └────── COMMIT ──────────────────────┘
      │                                        │
      ▼                                        ▼  (after commit, other thread)
 201 Created                        UserRegisteredListener.onUserRegistered
 (no password in body)                        │
                                              ▼
                                        MailService.send
                                              │
                                              ▼
                                    "…/api/v1/auth/verify?token=<uuid>"

user clicks the link
      │
      ▼
AuthController.verify → AuthService.verify
      ├─ token unknown?  → 400 "Invalid activation link"
      ├─ usedAt != null? → 400 "Link already used"
      ├─ expired?        → 400 "Link expired, please request a new one"
      └─ user.setEnabled(true); token.setUsedAt(now)     ← dirty checking, no save()
      │
      ▼
 200 {"message": "Account verified successfully"}
```

Until that last step, `login` returns **401** — `AppUserDetailsService` reports the account as disabled and the `AuthenticationManager` refuses it.

Lost the email? `POST /api/v1/auth/resend` mints a new token and republishes the event.

### 2. Login

```
POST /api/v1/auth/login  {"email": "...", "password": "..."}
      │
      ▼
AuthService.login → authManager.authenticate(UsernamePasswordAuthenticationToken)
      │                    │
      │                    ├─ AppUserDetailsService.loadUserByUsername(email)
      │                    ├─ BCrypt compare        → BadCredentialsException → 401
      │                    └─ enabled check         → DisabledException       → 401
      ▼
JwtService.generateToken → subject=email, claims: userId + role, 24h expiry
      │
      ▼
 200 {"token": "eyJhbGciOiJIUzI1NiJ9…"}
```

### 3. An authenticated request

```
GET /api/v1/orders     Authorization: Bearer eyJ…
      │
      ▼
JwtAuthFilter
      ├─ no header / not Bearer → continue anonymous
      ├─ jwtService.parse → signature + expiry checked (throws ⇒ stay anonymous)
      ├─ loadUserByUsername(claims.subject)
      └─ SecurityContext ← UsernamePasswordAuthenticationToken(user, null, authorities)
      │
      ▼
SecurityFilterChain rules
      ├─ anonymous + protected URL → 401 (HttpStatusEntryPoint)
      ├─ authenticated, wrong role → 403
      └─ allowed                   → controller
      │
      ▼
@AuthenticationPrincipal UserDetails principal    ← injected from the SecurityContext
```

### 4. Placing an order

```
POST /api/v1/orders   {"items":[{"productId":"…","quantity":2}]}
      │
      ▼
OrderController.createOrder(principal, dto)      email comes from the JWT, not the body
      │
      ▼
OrderService.create(email, dto)   ┌── @Transactional ────────────────────────┐
      ├─ findByEmail                → 404 if unknown                         │
      ├─ Set<UUID> of product ids    dedupes                                  │
      ├─ findAllById  ← ONE query    avoids N+1                               │
      ├─ any id missing?             → 404, before touching stock             │
      ├─ for each item:                                                       │
      │    ├─ stock < quantity?      → 409, rolls back everything above       │
      │    ├─ product.setStock(-n)   dirty checking writes the UPDATE         │
      │    ├─ new OrderItem(unitPrice = product.price)   price snapshot       │
      │    └─ total += unitPrice × quantity                                   │
      └─ orderRepository.save(order) cascade = ALL saves the items too        │
                                    └────── COMMIT ────────────────────────────┘
      │
      ▼
 201 Created + Location: /api/v1/orders/{id}
```

---

## API reference

Base URL `http://localhost:8080`. Controllers carry the full `/api/v1/...` path — `server.servlet.context-path` is commented out, so do not add the prefix twice.

### Auth — public

| Method | Path | Body | Success |
|---|---|---|---|
| POST | `/api/v1/auth/register` | `firstName, lastName, email, password, role` | 201 + user |
| POST | `/api/v1/auth/login` | `email, password` | 200 + `{token}` |
| GET | `/api/v1/auth/verify?token=…` | — | 200 + message |
| POST | `/api/v1/auth/resend` | `email` | 200 + message |

### Products

| Method | Path | Access |
|---|---|---|
| GET | `/api/v1/products` | public, paged (`page`, `size`, `sort`) |
| GET | `/api/v1/products/{id}` | public |
| POST | `/api/v1/products` | **ADMIN** |
| PUT | `/api/v1/products/{id}` | **ADMIN** |
| DELETE | `/api/v1/products/{id}` | **ADMIN** |

### Orders — authenticated

| Method | Path | Does |
|---|---|---|
| POST | `/api/v1/orders` | place an order for the token's owner |
| GET | `/api/v1/orders` | list the token owner's orders |

### Walkthrough

```bash
# 1. register — account starts disabled
curl -X POST http://localhost:8080/api/v1/auth/register \
  -H "Content-Type: application/json" \
  -d '{"firstName":"Bashar","lastName":"Khoujah","email":"me@example.com","password":"Password123","role":"USER"}'

# 2. open http://localhost:8025, click the activation link (or curl it)

# 3. log in
curl -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"me@example.com","password":"Password123"}'

# 4. order, using the token from step 3
curl -X POST http://localhost:8080/api/v1/orders \
  -H "Authorization: Bearer <token>" \
  -H "Content-Type: application/json" \
  -d '{"items":[{"productId":"<product-uuid>","quantity":2}]}'
```

---

## Configuration

`application.properties`, with secrets pulled from `.env` via `spring.config.import=optional:file:.env[.properties]`.

| Property | Default | Purpose |
|---|---|---|
| `spring.datasource.url` | `jdbc:mysql://localhost:3306/ecommerce?…createDatabaseIfNotExist=true` | schema is created on first run |
| `spring.datasource.username` / `.password` | `${DB_USER:root}` / `${DB_PASSWORD}` | from `.env` |
| `spring.jpa.hibernate.ddl-auto` | `create-drop` | **see Known issues** |
| `spring.jpa.show-sql` | `true` | watch the batched SELECT and stock UPDATEs |
| `jwt.secret` | `${JWT_SECRET}` | ≥ 32 bytes or the app will not start |
| `jwt.expiration-ms` | `86400000` | 24 hours |
| `spring.mail.host` / `.port` | `${MAIL_HOST:localhost}` / `${MAIL_PORT:1025}` | Mailpit in dev |
| `app.mail.from` | `noreply@ecommerce.local` | **currently not wired up — see Known issues** |
| `app.base-url` | `${APP_BASE_URL:http://localhost:8080}` | prefix for activation links |

A `#` only starts a comment at the **start of a line** in a properties file. `DB_USER=root # postgres` sets the username to the literal string `root # postgres`.

---

## Testing

```bash
./mvnw test                      # all 15
./mvnw test -Dtest=ProductTest   # one class
```

Tests run against **H2 in memory**, never your MySQL. `src/test/resources/application-test.properties` swaps the datasource, and the Spring-booting tests opt in with `@ActiveProfiles("test")`.

```properties
spring.datasource.url=jdbc:h2:mem:testdb;MODE=MySQL;DB_CLOSE_DELAY=-1;NON_KEYWORDS=USER
```

- `MODE=MySQL` — H2 imitates the production dialect.
- `DB_CLOSE_DELAY=-1` — keeps the database alive between connections; without it H2 drops everything when the last connection closes, mid-run.
- `NON_KEYWORDS=USER` — H2 2.x treats `USER` as reserved. Redundant now that the entity maps to `users`, kept as a guard.

| Class | Kind | Tests | Covers |
|---|---|---|---|
| `EcommerceApplicationTests` | smoke | 1 | the context loads — an empty body is a real assertion, since a misconfigured bean fails startup |
| `ProductTest` | pure unit | 3 | `equals` by id, inequality, constant `hashCode` |
| `OrderTest` | pure unit | 1 | `equals` by id |
| `OrderServiceTest` | Mockito | 2 | the stock check throws and saves nothing; totals and decrements are right |
| `OrderServiceIntegrationTest` | Spring + H2 | 2 | a failed item **rolls back** the whole order; a good order persists |
| `OrderControllerTest` | `@WebMvcTest` | 3 | 201/400/409 mapping through the real filter chain |
| `OrderSecurityIntegrationTest` | Spring + MockMvc | 3 | no token → 401; USER deleting a product → 403; ADMIN → allowed |

**Why both unit and integration tests for the same service.** Run the two-item rollback scenario with mocks and the laptop reads `8` afterwards — mocks have no transaction, so a mutated Java object stays mutated. Only a real database can show the value reverting to `10`. Conversely, the mock test isolates arithmetic and branching and runs in milliseconds.

**`@WebMvcTest` notes**, learned the hard way in `OrderControllerTest`:

- It does **not** load a plain `@Configuration`, so `SecurityConfig` needs `@Import(SecurityConfig.class)`. Without it, Spring Security's defaults apply and every POST is a 403.
- It does not load `@Service` or repository beans, so `JwtService`, `AppUserDetailsService` and `UserRepository` all need `@MockitoBean`.
- `/api/v1/orders/**` requires authentication, so the class carries `@WithMockUser`.

---

## Known issues and what's missing

Found by review, verified against the code, not yet fixed. Roughly worst first.

### Security

1. **`AuthController.testMail` is an open mail relay.** `GET /api/v1/auth/test-mail` is `permitAll` and hardcodes a personal address. Anyone can loop it to flood that inbox and burn the sending domain's reputation. It is also the only reason `AuthController` injects `MailService`. **Delete it.**
2. **Self-registration can grant `ADMIN`.** `UserCreateRequest.role` is client-supplied and `AuthService` copies it straight to the entity. `POST /register {"role":"ADMIN"}` → verify → log in → the JWT carries `role: ADMIN`, which `SecurityConfig` honours for product writes. Registration should force `Role.USER`.
3. **`JwtAuthFilter` never checks `enabled`.** Only the login path runs the `AccountStatusUserDetailsChecker`. A user disabled *after* login keeps full access for the remaining life of their 24h token. Add `new AccountStatusUserDetailsChecker().check(user)` in the filter.
4. **`/resend` is an account-enumeration oracle.** It returns three distinguishable outcomes (no such account / already verified / sent), letting an attacker map which addresses exist. It also has no cooldown, so it can flood a victim's inbox and grow `verification_token` without bound. Return an identical 200 in all cases and add a per-email cooldown.
5. **Verification is a GET with the secret in the query string.** Link prefetchers (Outlook SafeLinks, Gmail scanners) consume the token before the human clicks, who then sees "Link already used" on an account that *is* verified. Make the already-verified case idempotent, and prefer a landing page that POSTs.
6. **Old tokens are never invalidated.** Verifying with the newest token leaves every earlier one live for its full 24 hours.

### Correctness

7. **`MailService.from` is never injected.** It has no `@Value` and is not `final`, so `@RequiredArgsConstructor` skips it and `app.mail.from` is dead config. Every message is sent with `from == null`. Mailpit accepts that, which is why dev looks fine — a real relay rejects it. Fix: `@Value("${app.mail.from}") private final String from;`.
8. **`ddl-auto=create-drop` against a persistent volume.** `compose.yaml` mounts `mysql_data`, so every restart drops and recreates the schema, wiping all data including just-verified accounts. The *test* profile uses `update`. The two are backwards; production should be `update`.
9. **`User.enabled` is `@Column(nullable = true)` on a primitive.** Meaningless from the Java side (a primitive is never null) but it drops the database guarantee on a security-relevant flag. A NULL from any other path makes Hibernate throw `PropertyAccessException` on load, 500-ing every request from that user.
10. **`Order` and `OrderItem` use `Objects.hash(id)`** while `Product` and `User` return a constant. The id-derived version is exactly the bug `ProductTest.hashCodeStaysTheSame` guards against — an order added to a `HashSet` before persist becomes unfindable once Hibernate assigns the id.
11. **Mail failures are swallowed.** `UserRegisteredListener` catches everything and logs. The transaction has already committed and the client already has its 201, so a dead SMTP server leaves a permanently disabled user with no email and nothing retrying.
12. **`InvalidTokenException` is reused for non-token conditions.** `resendVerification` throws it for "no such user" (should be 404) and "already verified" (should be 409); both surface as `400 {"message": "Invalid token"}`.

### Cleanup

13. The token-mint-and-publish block is copy-pasted verbatim between `register` and `resendVerification` — extract `issueToken(User)`.
14. `UserRegisteredEvent.userId` is never read by the listener.
15. `OrderController` injects `UserRepository` and uses it in `getOrdersByUserId` — a controller reaching past the service layer, breaking the rule stated at the top of this file. `OrderService` should expose an email-based lookup.
16. `UserService` and `UserController` are both dead. `DataSeeder` is entirely commented out.
17. `OrderService` still holds the whole previous `create(UUID, …)` implementation as a commented block.
18. `compose.yaml` has no `depends_on: mail` and does not set `APP_BASE_URL`; `.env.example` does not mention `MAIL_HOST`, `MAIL_PORT`, or `APP_BASE_URL`.

### Not built

Order status transitions and cancellation with stock restored · reading a single order by id · password reset · refresh tokens · a stock lock (two concurrent buyers of the last unit can both pass the check — needs an atomic conditional `UPDATE`, a pessimistic lock, or a `@Version` column) · tests for the auth and mail flow, which currently have none.
