# E-Commerce API

A learning project: a REST API for a simple online store, built with Spring Boot.

The domain is the classic e-commerce model — users browse a product catalog, place orders, and each order holds line items that snapshot the product and its price at purchase time.

## Status

Work in progress. The **product catalog** and **order placement** are exposed over HTTP. Authentication is not implemented yet — Spring Security is wired in but leaves the product and order endpoints open, and the order endpoint takes the buyer's id as a query parameter instead of reading it from a logged-in principal (see [Why `userId` is a parameter](#why-userid-is-a-query-parameter)).

A test user is seeded into the database on startup so orders can be placed without a registration endpoint.

## Tech stack

| Piece | Choice |
|---|---|
| Language | Java 17 |
| Framework | Spring Boot 4.1.0 (Web MVC, Data JPA, Security, Validation) |
| Database | MySQL 8 (schema auto-created by Hibernate `ddl-auto=update`) |
| Build | Maven (`./mvnw`) |
| Boilerplate | Lombok |

## Project layout

```
src/main/java/com/apps/ecommerce/
├── config/       SecurityConfig — filter chain, BCrypt password encoder
│                 DataSeeder — inserts the test user on startup
├── controller/   ProductController, OrderController — HTTP layer
├── dto/          Request/response records (Product*, Order*, OrderItem*)
├── entity/       Product, User, Order, OrderItem (JPA)
├── enums/        Role (USER, ADMIN), OrderStatus (PENDING, PAID, SHIPPED, CANCELLED)
├── exception/    Custom exceptions + GlobalExceptionHandler
├── repository/   Spring Data JPA repositories
└── service/      ProductService, OrderService — business logic, entity ↔ DTO mapping
```

The layering is deliberate: controllers only handle HTTP, services own the logic and the entity↔DTO mapping, and repositories only touch the database. Entities never leave the service layer — the API speaks in DTOs.

## Data model

- **Product** — `id` (UUID), `name`, `price`, `stock`, `description`
- **User** — `id` (UUID), `email` (unique), `password` (BCrypt-hashed), `role`, `enabled`
- **Order** — `id` (UUID), owning `user`, `status`, `totalPrice`, `createdAt`, list of items. Table is named `orders` because `order` is a reserved SQL word.
- **OrderItem** — links an order to a product with `quantity` and `unitPrice`. The price is copied onto the line item so historical orders don't change when the catalog price changes.

`User`'s table is named `users` for the same reason `Order`'s is `orders` — `USER` is a reserved word in standard SQL. MySQL happens to allow it, H2 does not, so the unquoted name only broke once tests ran against H2.

All primary keys are UUIDs. `Product`, `Order`, and `OrderItem` let Hibernate generate them via `@GeneratedValue(strategy = GenerationType.UUID)`. `User` is the exception, because the seeder needs to pin a fixed, known UUID, and that takes two changes working together:

- **No `@GeneratedValue`.** Hibernate's UUID generator runs before every insert and overwrites whatever id you assigned. A `@PrePersist` hook fills the id in instead — only when it's still null, so an explicitly set id survives.
- **`implements Persistable<UUID>`.** Spring Data picks between `persist()` and `merge()` by calling `isNew()`, which by default just tests `id == null`. With the id assigned up front, a brand-new user looks detached, goes down `merge()`, and fails with `StaleObjectStateException` — merge finds no row to merge into. A `@Transient boolean isNew` flag, cleared by `@PostPersist`/`@PostLoad`, answers the question honestly.

## The test user

`config/DataSeeder.java` runs a `CommandLineRunner` after the application context starts and inserts one user if it isn't already there. No registration endpoint needed.

It's **idempotent** — it looks the user up by email first, so restarting the app doesn't create duplicates or reset the password.

```
id       : 11111111-1111-1111-1111-111111111111
email    : test@example.com
password : password123
```

The same values are printed as a banner in the startup log. The id is a fixed constant, not random, so you can hardcode it in Postman/curl and it survives restarts.

Configured in `application.properties`:

```properties
app.seed.enabled=true
app.seed.user.id=11111111-1111-1111-1111-111111111111
app.seed.user.email=test@example.com
app.seed.user.password=password123
```

Set `app.seed.enabled=false` to turn the seeder off entirely (the bean is behind `@ConditionalOnProperty`). The password is BCrypt-hashed with the same `PasswordEncoder` bean the rest of the app uses, so the row is a realistic user, not a special case.

> If you ran the app before the seeder existed and a `test@example.com` row is already there with a random id, the seeder will leave it alone. Delete that row to get the fixed id:
> `DELETE FROM users WHERE email = 'test@example.com';`

## API

Base URL: `http://localhost:8080`. Controllers carry the full `/api/v1/...` path themselves — `server.servlet.context-path` is commented out in `application.properties`, so don't add the prefix twice.

### Products

| Method | Path | Description |
|---|---|---|
| `GET` | `/api/v1/products` | Paged list of products |
| `GET` | `/api/v1/products/{id}` | One product by UUID |
| `POST` | `/api/v1/products` | Create a product |
| `PUT` | `/api/v1/products/{id}` | Replace a product |
| `DELETE` | `/api/v1/products/{id}` | Delete a product |

`GET /api/v1/products` accepts the standard Spring pagination query params — `page`, `size`, `sort`. It defaults to `size=20` sorted by `name`.

### Orders

| Method | Path | Description |
|---|---|---|
| `POST` | `/api/v1/orders?userId={uuid}` | Place an order |

Request body:

```json
{
  "items": [
    { "productId": "…uuid…", "quantity": 2 },
    { "productId": "…uuid…", "quantity": 1 }
  ]
}
```

Responds `201 Created` with a `Location` header pointing at the new order, and the created order in the body:

```json
{
  "id": "…uuid…",
  "status": "PENDING",
  "totalPrice": 109.97,
  "createdAt": "2026-08-08T12:00:00",
  "items": [
    { "productId": "…uuid…", "productName": "Keyboard", "quantity": 2, "unitPrice": 49.99 }
  ]
}
```

Note the response never contains the user — the client already knows who placed it, and leaking user data into every order payload isn't useful.

## How order creation works

All of this lives in `OrderService.create(UUID userId, OrderCreateRequest dto)`, which is annotated `@Transactional`. That single annotation is what makes the rest safe: everything below either happens completely or not at all.

### Why `userId` is a query parameter

An order needs an owner — `Order.user` is `optional = false` and the `user_id` column is `NOT NULL`. Something has to say who is buying.

Normally that comes from the **authenticated principal**: the JWT filter puts the user in the `SecurityContext`, and the controller reads it. Authentication isn't built yet, so there is nothing to read.

The buyer's id is therefore passed explicitly as `?userId=…`. Two deliberate choices here:

1. **It is a query parameter, not a field in the request body.** The body describes *what is being bought*; who is buying is a separate concern. Keeping them apart means that when auth lands, the fix is a one-line change in `OrderController` — replace the `@RequestParam UUID userId` with the id from the security context — and `OrderCreateRequest` never changes. If the id were a body field, every client would have to be updated too.
2. **This is a development-only shortcut.** Trusting a client-supplied user id means anyone can place an order as anyone else. That is fine while the endpoint is `permitAll()` on localhost, and must be removed before this is exposed to anything real.

### Step by step

1. **Load the buyer.**

   ```java
   User user = userRepository.findById(userId)
           .orElseThrow(() -> new ResourceNotFoundException(User.class, userId));
   ```

   Unknown id → `404`, before anything else is touched.

2. **Collect the product ids into a `Set`.** The set deduplicates, so asking for the same product on two lines doesn't fetch it twice.

3. **Fetch every product in one query** with `productRepository.findAllById(ids)`, then index them into a `Map<UUID, Product>`.

   This is the important part: the naive version calls `findById` inside the item loop, which is one SELECT per line item — the N+1 problem. One `SELECT … WHERE id IN (…)` gives the same data in a single round trip regardless of basket size.

4. **Verify nothing is missing.** If the map is smaller than the id set, some product id didn't exist. The missing ids are computed by removing the found ones, and a `404` names one of them.

   Doing this check up front means a bad basket fails before any stock is touched.

5. **Build the order shell** — attach the user, set `status = PENDING`, start the running total at `BigDecimal.ZERO`.

   `PENDING` because placing an order is not paying for it. Payment moves it to `PAID` later; the enum already has the states.

6. **Walk the requested items.** For each line:
   - look the product up in the map (no database call — it's already in memory),
   - **check stock**, and reject the whole order if it's short,
   - **decrement stock**,
   - build an `OrderItem` with `unitPrice` copied from the product's current price,
   - link it both ways (`item.setOrder(newOrder)` and `newOrder.getItems().add(item)`),
   - add `unitPrice × quantity` to the running total.

7. **Save once.** `orderRepository.save(newOrder)` persists the order, and `@OneToMany(cascade = CascadeType.ALL)` on `Order.items` persists every line item with it. There is no separate `orderItemRepository.save(…)` loop.

8. **Map to a DTO** and return. The entity never leaves the service.

### Money

Prices are `BigDecimal` everywhere — never `double`. Binary floating point cannot represent `0.1` exactly, so `double` totals drift by fractions of a cent and eventually disagree with what the customer was shown. `BigDecimal.add` / `.multiply` are exact.

The total is computed from `unitPrice × quantity` on the server. It is never taken from the request — a client-supplied total is a client-controlled price.

## How stock is handled

Stock lives on `Product.stock` and is adjusted as part of placing the order.

**Check before decrement, per line:**

```java
if (product.getStock() < orderRequest.quantity()) {
    throw new InsufficientStockException(
            product.getName(), product.getStock(), orderRequest.quantity());
}
product.setStock(product.getStock() - orderRequest.quantity());
```

`InsufficientStockException` maps to **409 Conflict** in `GlobalExceptionHandler`, with a message naming the product, what's available, and what was asked for. 409 rather than 400 because the request is well-formed — it's the current state of the world that makes it impossible.

**No explicit save.** The `Product` objects came from `findAllById` inside a transaction, so they are *managed* entities. Hibernate tracks changes to managed objects (dirty checking) and issues the `UPDATE product SET stock = …` statements automatically at commit. Calling `productRepository.save(product)` would be a no-op.

**All-or-nothing.** Because the method is `@Transactional`, throwing part-way through rolls back everything. So an order for three products where the third is out of stock does not quietly decrement the first two — those in-memory decrements are discarded with the rest of the transaction. No half-placed orders, no leaked stock.

**Price snapshot.** `unitPrice` is copied onto the `OrderItem` at purchase time rather than read through the product relation later. Change the catalog price tomorrow and yesterday's orders keep the amount actually charged. Same reasoning as `Order.totalPrice` being a stored column rather than a computed one.

### Known limitation: the race

Two requests buying the last unit at the same time can both read `stock = 1`, both pass the check, and both decrement — leaving `stock = -1` and two orders that can't both be fulfilled. Read-then-write without a lock is not atomic, and `@Transactional` alone does not prevent it (MySQL's default `REPEATABLE READ` isolation doesn't serialize these two transactions).

The fixes, roughly in order of how much they cost:

- an atomic conditional update — `UPDATE product SET stock = stock - :qty WHERE id = :id AND stock >= :qty`, and treat "0 rows affected" as out of stock;
- a pessimistic lock on the fetch — `@Lock(LockModeType.PESSIMISTIC_WRITE)` on the repository method, which issues `SELECT … FOR UPDATE`;
- optimistic locking — a `@Version` column on `Product`, and retry on `OptimisticLockException`.

None are implemented yet. Single-user local testing won't hit this.

### Other current behaviour worth knowing

- Sending the same `productId` on two separate lines is accepted. The product is fetched once, but the loop runs twice, so stock is decremented twice and two `OrderItem` rows are written. The total is correct; the order just has two lines for one product instead of a merged one.
- Nothing puts stock back. Cancelling an order does not restore it — `OrderStatus.CANCELLED` exists but no code transitions to it yet.

## Error format

`GlobalExceptionHandler` turns exceptions into a consistent JSON body:

```json
{
  "status": 400,
  "message": "Not valid",
  "error": "...",
  "timestamp": "2026-08-07T12:00:00",
  "fields": { "name": "name is required" }
}
```

`fields` is only populated for validation errors; it is `null` otherwise.

| Situation | Status |
|---|---|
| `@Valid` failure on the request body | 400 |
| `AuthenticationException` | 401 |
| `ResourceNotFoundException` — unknown user or product id | 404 |
| `DuplicateResourceException` | 409 |
| `InsufficientStockException` | 409 |
| Any other exception | 500 |

Validation on the order body is declarative, in the records themselves: `items` is `@NotEmpty`, each entry needs a `@NotNull productId` and a `quantity` that is `@NotNull @Min(1)`. The `@Valid` on the list is what makes Bean Validation descend into the nested records — without it only the list itself would be checked. So an empty basket or a quantity of `0` is rejected as a `400` before `OrderService` ever runs.

## Running it

**Prerequisites:** JDK 17+, and a MySQL server on `localhost:3306`. The connection URL uses `createDatabaseIfNotExist=true`, so the `ecommerce` schema is created on first run — you only need MySQL itself running.

Database credentials come from environment variables, loaded from a gitignored `.env` file in the project root (see `.env.example`):

```properties
DB_USER=root
DB_PASSWORD=your_password
```

```bash
./mvnw spring-boot:run
```

The app starts on port 8080 and logs the test user's credentials. Hibernate logs the SQL it runs (`spring.jpa.show-sql=true`), which is handy while learning — you can watch the single batched product `SELECT` and the stock `UPDATE` statements go by while placing an order.

To build a jar instead:

```bash
./mvnw clean package
java -jar target/ecommerce-0.0.1-SNAPSHOT.jar
```

### Full example

```bash
# 1. create a product, note the returned id
curl -X POST http://localhost:8080/api/v1/products \
  -H "Content-Type: application/json" \
  -d '{"name":"Keyboard","price":49.99,"stock":10,"description":"Mechanical, 60%"}'

# 2. place an order for it as the seeded test user
curl -X POST "http://localhost:8080/api/v1/orders?userId=11111111-1111-1111-1111-111111111111" \
  -H "Content-Type: application/json" \
  -d '{"items":[{"productId":"<product-id-from-step-1>","quantity":2}]}'

# 3. confirm stock dropped from 10 to 8
curl http://localhost:8080/api/v1/products/<product-id-from-step-1>
```

## Notes and next steps

- **Auth is the big missing piece.** Once JWT login exists, `userId` comes out of the query string and the order endpoint stops being `permitAll()`.
- **Stock needs a lock** before concurrent traffic — see [the race](#known-limitation-the-race).
- **Reading orders isn't implemented.** `GET /api/v1/orders` and `GET /api/v1/orders/{id}` don't exist yet, and neither do status transitions (`PENDING → PAID → SHIPPED`, or `CANCELLED` with stock restored).
- **Copy-paste bug in `equals()`.** `Order` and `OrderItem` both test `o instanceof Product`, so their `equals` always returns `false` against another instance of their own type. `User` has been fixed; these two still need it.
- **More tests.** `OrderService.create` is covered for the stock check, the total, and the rollback. Still open: the unknown-product 404, the duplicate-`productId` case, and the controller layer.

## Testing

```bash
./mvnw test                      # everything
./mvnw test -Dtest=ProductTest   # one class
```

Four test classes, 7 tests.

| Class | Kind | Boots Spring? | Touches a database? | Proves |
|---|---|---|---|---|
| `EcommerceApplicationTests` | smoke | yes | yes (H2) | the app is wired correctly |
| `ProductTest` | unit | no | no | `equals` / `hashCode` on an entity |
| `OrderServiceTest` | unit (Mockito) | no | no | order logic — total, stock check, exception |
| `OrderServiceIntegrationTest` | integration | yes | yes (H2) | the transaction really rolls back |

### Where tests get their database

Never your MySQL. `src/test/resources/application-test.properties` swaps the datasource:

```properties
spring.datasource.url=jdbc:h2:mem:testdb;MODE=MySQL;DB_CLOSE_DELAY=-1;NON_KEYWORDS=USER
spring.jpa.hibernate.ddl-auto=create-drop
```

- `h2:mem:testdb` — the whole database lives in RAM and vanishes when the JVM exits.
- `MODE=MySQL` — H2 imitates MySQL's SQL dialect so the queries behave like production.
- `DB_CLOSE_DELAY=-1` — keeps the database alive between connections; without it H2 drops everything the moment the last connection closes, mid-run.
- `NON_KEYWORDS=USER` — H2 2.x treats `USER` as a reserved word, so an unquoted `user` table is a syntax error. Belt-and-braces now that the entity uses `@Table(name = "users")`.
- `create-drop` — schema rebuilt from the entities at startup, dropped at shutdown. Every run starts clean.

The two `@SpringBootTest` classes opt in with `@ActiveProfiles("test")`. That annotation is what loads the `-test` file — without it they'd connect to your real MySQL.

### Vocabulary

- **Mock** — a fake object that looks like the real one, but you decide what it returns. `userRepository` in `OrderServiceTest` is a fake: it has the same methods, but no database behind it.
- **Stub** — teaching the mock one answer: `when(x.find(id)).thenReturn(user)` means "if anyone calls `find` with this id, hand back this user." Anything you don't stub returns `null` (or an empty `Optional`).
- **Verify** — asserting on *behaviour* rather than a value: "was `save` called?" Regular assertions check what a method returned; `verify` checks what it did.
- **Arrange / Act / Assert** — the three phases of a test. Build the situation, run the one thing under test, then check the outcome. The comments in `ProductTest` mark them explicitly.

---

### `EcommerceApplicationTests`

```java
@ActiveProfiles("test")
@SpringBootTest
class EcommerceApplicationTests {
    @Test
    void contextLoads() { }
}
```

The body is empty on purpose — that isn't laziness.

- `@SpringBootTest` starts the entire application: component scan, every bean, JPA, Hibernate's schema generation, Spring Security.
- If any bean is misconfigured — a missing dependency, a bad property, a broken entity mapping — startup throws and the test fails.
- So an empty method is a real assertion: *the application can start.* This is the test that caught both the H2 reserved-word failure and the `StaleObjectStateException` from the seeder.
- `@ActiveProfiles("test")` points it at H2 instead of MySQL.

---

### `ProductTest` — a pure unit test

No Spring, no database, no mocks. It constructs objects with `new` and checks two methods. Runs in milliseconds.

**`sameIdMeansEqual`**

```java
UUID id = UUID.randomUUID();     // one id, shared
Product a = new Product();
a.setId(id);
a.setName("a");

Product b = new Product();
b.setId(id);                     // same id
b.setName("b");                  // different name — deliberately

assertEquals(a, b);
```

Two objects, same id, **different names**, and the assertion says they're equal. That's the point: `Product.equals` compares `id` and nothing else. For a JPA entity, identity means "same database row", not "same field values" — a product whose name you just edited is still the same product.

**`differentIdMeansNotEqual`**

Same shape, but each product gets its own `UUID.randomUUID()`, and the assertion flips to `assertNotEquals`. Different rows, so not equal — even though both are `Product` objects with the same shape.

**`hashCodeStaysTheSame`**

```java
Product p = new Product();
int before = p.hashCode();       // Arrange — id is still null here

p.setId(UUID.randomUUID());      // Act — Hibernate does this on insert

assertEquals(before, p.hashCode());   // Assert — unchanged
```

This guards the odd-looking `return getClass().hashCode();` in the entity.

Why it matters: put a new `Product` in a `HashSet` before saving it, and the set files it under its current hash. Hibernate then assigns the id at insert time. If `hashCode` were built from the id, it would now return something different, the object would be in the wrong bucket, and `set.contains(p)` would say **false** for an object the set is literally holding. A constant hash can never drift, so the entity stays findable.

---

### `OrderServiceTest` — unit test with mocks

```java
@ExtendWith(MockitoExtension.class)
public class OrderServiceTest {

    @Mock private OrderRepository orderRepository;
    @Mock private ProductRepository productRepository;
    @Mock private UserRepository userRepository;

    @InjectMocks private OrderService orderService;
```

- `@ExtendWith(MockitoExtension.class)` — plugs Mockito into JUnit 5. It creates fresh mocks before each test and, after each test, fails the run if you stubbed something that was never used (strict stubs — it catches tests that don't do what you think).
- `@Mock` — a fake repository. Same interface, no database, every method returns `null`/empty until stubbed.
- `@InjectMocks` — builds the **real** `OrderService` and pushes the three fakes into it. This works because `OrderService` uses `@RequiredArgsConstructor`, so Mockito has a constructor to hand them to.

Net effect: real business logic, fake data layer. Nothing boots, nothing connects, the test runs in milliseconds.

**`throwsWhenStockNotEnough`**

```java
Product product = new Product();
product.setStock(3);                                            // only 3 in stock

when(userRepository.findById(userId)).thenReturn(Optional.of(user));
when(productRepository.findAllById(any())).thenReturn(List.of(product));

var request = new OrderCreateRequest(List.of(new OrderItemRequest(productId, 5)));   // asking for 5

assertThrows(InsufficientStockException.class, () -> orderService.create(userId, request));

assertEquals(3, product.getStock());
verify(orderRepository, never()).save(any());
```

Line by line:

1. Build a product with **3** in stock. It's a plain object — no database anywhere.
2. `when(userRepository.findById(userId)).thenReturn(Optional.of(user))` — stub the user lookup so the service gets past its first step. Without this the mock returns an empty `Optional` and the service throws `ResourceNotFoundException` instead, testing the wrong thing.
3. `when(productRepository.findAllById(any()))` — `any()` is an argument matcher meaning "called with anything". The service builds that id `Set` internally, so matching it exactly would be brittle.
4. The request asks for **5** against a stock of 3 — this is what must trip the check. (Asking for fewer than 3 was the original bug: no exception, execution ran on to an unstubbed `save()` returning `null`, and `toDto(null)` threw an NPE.)
5. `assertThrows(InsufficientStockException.class, …)` — runs the lambda and fails unless *that specific type* is thrown. The lambda is required so the exception happens inside the assertion rather than blowing up the test method.
6. `assertEquals(3, product.getStock())` — stock untouched. The service throws before decrementing.
7. `verify(orderRepository, never()).save(any())` — a behaviour check: no order was ever persisted. A rejected order must leave nothing behind.

**`createsOrderCorrectly`**

```java
product.setPrice(new BigDecimal("25.50"));
product.setStock(10);

when(orderRepository.save(any(Order.class))).thenAnswer(i -> i.getArgument(0));

var request = new OrderCreateRequest(List.of(new OrderItemRequest(productId, 2)));
OrderResponse response = orderService.create(userId, request);

assertEquals(0, new BigDecimal("51.00").compareTo(response.totalPrice()));
assertEquals(8, product.getStock());
assertEquals(OrderStatus.PENDING, response.status());
```

- `thenAnswer(i -> i.getArgument(0))` — "whatever `Order` you're handed, give it straight back." A real repository returns the saved entity; an unstubbed mock returns `null`, and the service would then NPE on `toDto(null)`. This one line stands in for the database round trip.
- `assertEquals(0, …compareTo(…))` instead of `assertEquals(expected, actual)` — `BigDecimal.equals` compares **scale as well as value**, so `51.0` and `51.00` are not equal to it. `compareTo` returns `0` when the numeric values match regardless of scale. Always compare money with `compareTo`.
- `assertEquals(8, product.getStock())` — 10 minus 2. The service mutates the object it was handed, so the test can read the decrement directly.
- `assertEquals(OrderStatus.PENDING, response.status())` — a new order is placed, not paid.

---

### `OrderServiceIntegrationTest` — the real thing

```java
@SpringBootTest
@ActiveProfiles("test")
public class OrderServiceIntegrationTest {

    @Autowired private OrderService orderService;
    @Autowired private ProductRepository productRepository;
    // …
```

No mocks here. `@Autowired` pulls the actual beans out of a running Spring context, and the repositories talk to real H2 tables through real transactions.

**`setUp` — runs before every test**

```java
orderRepository.deleteAll();
productRepository.deleteAll();
userRepository.deleteAll();
```

Order matters. Orders reference users and products by foreign key, so children go first — deleting a product still referenced by an order line would violate the constraint. (`OrderItem` rows go with their order automatically, via `cascade = ALL` + `orphanRemoval` on `Order.items`.)

Then it seeds a user and two products — a **laptop with stock 10** and a **mouse with stock 1** — keeping their generated ids in fields. `@BeforeEach` means every test starts from this exact state, so tests can't leak into each other.

**`rollsBackEverythingWhenOneItemFails`**

```java
var request = new OrderCreateRequest(List.of(
        new OrderItemRequest(laptopId, 2),   // fine — 10 in stock
        new OrderItemRequest(mouseId, 5)));  // fails — only 1 in stock

assertThrows(InsufficientStockException.class,
        () -> orderService.create(userId, request));

assertEquals(10, productRepository.findById(laptopId).get().getStock());
assertEquals(0, orderRepository.count());
```

The basket is deliberately ordered so the **first** line succeeds and the **second** fails. Walking the items, the service decrements the laptop from 10 to 8, then hits the mouse and throws.

The two assertions are the whole point:

- `assertEquals(10, …getStock())` — re-read from the database, the laptop is back at **10**, not 8. The decrement was undone.
- `assertEquals(0, orderRepository.count())` — no half-written order survived.

That's `@Transactional` on `OrderService.create` doing its job: `InsufficientStockException` is unchecked, so `jakarta.transaction.Transactional` rolls the transaction back, discarding both the stock update and the partial order.

**Why this can't be a mock test.** Run the same two-item scenario in `OrderServiceTest` and the laptop object would read **8** afterwards. Mocks have no transaction and no rollback — a plain Java object stays mutated. Only a real database with a real transaction can demonstrate that the change was actually reverted.

### Which kind to write

- Reach for the **mock test** for logic: calculations, branching, which exception, whether a collaborator was called. It's fast, and a failure points at one method.
- Reach for the **integration test** when the framework is the thing under test: transactions and rollback, JPA mappings, cascades, queries, constraints. It's slower — a full context boot — but it's the only place those behaviours actually exist.