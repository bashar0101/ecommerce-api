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

All primary keys are UUIDs. `Product`, `Order`, and `OrderItem` let Hibernate generate them via `@GeneratedValue(strategy = GenerationType.UUID)`. `User` is the exception — it assigns its own id in a `@PrePersist` hook, because Hibernate's generator overwrites any id you set by hand, and the seeder needs to pin a fixed, known UUID.

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
> `DELETE FROM user WHERE email = 'test@example.com';`

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
- **No tests yet.** `OrderService.create` is the obvious first target — the stock check, the rollback on failure, and the total calculation are all pure logic worth pinning down.



# Testing
## What a mock is

- **A mock is a fake object that looks like the real one, but you decide what it returns.** You tell it: "when someone calls findById with this id, give back this user." It never touches MySQL.