# The tests, explained

This project has **34 tests** in **8 files**. This page explains every one of them.

Run them all:

```bash
./mvnw test
```

---

## Contents

- [First: the words you need](#first-the-words-you-need)
- [Two kinds of test](#two-kinds-of-test)
- [The 8 files](#the-8-files)
- [1. EcommerceApplicationTests](#1-ecommerceapplicationtests--1-test)
- [2. ProductTest](#2-producttest--3-tests)
- [3. OrderTest](#3-ordertest--1-test)
- [4. RateLimiterTest](#4-ratelimitertest--3-tests)
- [5. OrderServiceTest](#5-orderservicetest--2-tests)
- [6. OrderServiceIntegrationTest](#6-orderserviceintegrationtest--2-tests)
- [7. OrderControllerTest](#7-ordercontrollertest--3-tests)
- [8. OrderSecurityIntegrationTest](#8-ordersecurityintegrationtest--3-tests)
- [9. AuthServiceIntegrationTest](#9-authserviceintegrationtest--16-tests)
- [Where do the tests save data?](#where-do-the-tests-save-data)

---

## First: the words you need

### The three steps of a test

Almost every test has three parts:

```java
Product p = new Product();       // 1. Arrange - you build the situation
p.setId(UUID.randomUUID());      // 2. Act     - you do the one thing you want to test
assertEquals(before, p.hash());  // 3. Assert  - you check the result
```

### The annotations (the words with `@`)

| Word | What it means |
|---|---|
| `@Test` | "This method is a test." Without it, nothing runs. |
| `@DisplayName("...")` | A nice name. You see it in the report. |
| `@BeforeEach` | Run this **before every test** in the file. Good for cleaning. |
| `@SpringBootTest` | Start the **whole app** for this test. Slow but real. |
| `@ExtendWith(MockitoExtension.class)` | "I will use fake objects." No Spring here. Very fast. |
| `@WebMvcTest(X.class)` | Start **only** the controller `X`. Not the services, not the database. |
| `@ActiveProfiles("test")` | Use the test settings, so we use H2 and not your real database. |
| `@AutoConfigureMockMvc` | Give me a `MockMvc` object, so I can send fake HTTP requests. |
| `@Autowired` | "Spring, give me this object." |
| `@Mock` | Make a **fake** object. |
| `@MockitoBean` | Make a fake object and put it **inside Spring**. |
| `@InjectMocks` | Make the **real** object, and push the fakes inside it. |
| `@WithMockUser` | "Do this test like a user who is already logged in." |

### The checks (the `assert` words)

| Word | It passes when |
|---|---|
| `assertEquals(a, b)` | a and b are the same |
| `assertNotEquals(a, b)` | a and b are different |
| `assertTrue(x)` | x is true |
| `assertFalse(x)` | x is false |
| `assertNotNull(x)` | x is not null |
| `assertThrows(E.class, () -> ...)` | the code throws the error `E` |

### Fake objects (mocks)

A **mock** is a fake. It looks like the real class, but you decide the answer:

```java
when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(user));
```

This means: *if somebody asks for this email, give this user. Do not go to the database.*

You can also **check what happened**:

```java
verify(orderRepository, never()).save(any());
```

This means: *the code must never call `save`.*

| Word | Meaning |
|---|---|
| `when(...).thenReturn(x)` | when somebody calls this, give x |
| `when(...).thenThrow(e)` | when somebody calls this, throw the error e |
| `thenAnswer(i -> i.getArgument(0))` | give back the same object you received |
| `verify(x).method()` | check that `method` was called |
| `never()` | it must be called 0 times |
| `times(1)` | it must be called exactly 1 time |
| `any()` | I do not care about the value |

---

## Two kinds of test

This is the most important idea on this page.

**Unit test** — fast, with fakes:

```java
@ExtendWith(MockitoExtension.class)   // no Spring, no database
```

It runs in some milliseconds. When it fails, you know exactly where the problem is.
But a fake has **no transaction** and **no database**.

**Integration test** — slow, but real:

```java
@SpringBootTest                       // the whole app, a real database
```

It takes some seconds. But only here you can test a transaction, or a real query.

**Example:** the order test.
An order has 2 lines. Line 2 has no stock. The stock of line 1 must go back.

- In the **unit** test the stock stays at 8. A Java object does not go back alone.
- In the **integration** test the stock goes back to 10. This is the real answer.

So the same rule needs **two tests**, one of each kind.

---

## The 8 files

| File | Tests | Kind | It tests |
|---|---|---|---|
| `EcommerceApplicationTests` | 1 | integration | the app starts |
| `ProductTest` | 3 | unit | `equals` and `hashCode` |
| `OrderTest` | 1 | unit | `equals` |
| `RateLimiterTest` | 3 | unit (mocks) | counting login tries |
| `OrderServiceTest` | 2 | unit (mocks) | the order rules |
| `OrderServiceIntegrationTest` | 2 | integration | a real order |
| `OrderControllerTest` | 3 | web slice | the HTTP codes |
| `OrderSecurityIntegrationTest` | 3 | integration | who can do what |
| `AuthServiceIntegrationTest` | 16 | integration | register, login, email, password |

---

## 1. EcommerceApplicationTests — 1 test

```java
@ActiveProfiles("test")
@SpringBootTest
class EcommerceApplicationTests {
    @Test
    void contextLoads() { }
}
```

**The method is empty.** This looks strange, but it is a real test.

`@SpringBootTest` starts the whole app: it makes every bean, it opens the database, it builds the security. If **one** thing is wrong, the app does not start and the test fails.

So the empty method means: *the application can start*.

This test already found two real problems in this project:

- H2 did not accept a table with the name `user`
- the app tried to save a user with an id that was already there

---

## 2. ProductTest — 3 tests

No Spring. No database. Only `new Product()`. Very fast.

### `sameIdMeansEqual`

```java
UUID id = UUID.randomUUID();
Product a = new Product();  a.setId(id);  a.setName("a");
Product b = new Product();  b.setId(id);  b.setName("b");   // different name!
assertEquals(a, b);
```

Two products. **Same id, different name.** And the test says they are equal.

This is not a mistake. For a database object, "same" means **the same line in the table**. The name can change tomorrow. It is still the same product.

### `differentIdMeansNotEqual`

The same, but every product gets its own id. Now they are **not** equal.

### `hashCodeStaysTheSame`

```java
Product p = new Product();
int before = p.hashCode();      // the id is still null
p.setId(UUID.randomUUID());     // Hibernate does this when it saves
assertEquals(before, p.hashCode());
```

This protects the strange line in `Product`:

```java
public int hashCode() { return getClass().hashCode(); }   // always the same number
```

**Why?** Imagine you put a new product in a `HashSet`. The set puts it in a box, and it chooses the box with the hash number. Then Hibernate saves it and gives it an id. If the hash used the id, the number would change now. The product would be in the wrong box, and `set.contains(p)` would say **no** — for a product that is inside the set!

A number that never changes cannot make this problem.

---

## 3. OrderTest — 1 test

The same idea as `ProductTest`, but for `Order`: two orders with the same id are equal.

---

## 4. RateLimiterTest — 3 tests

```java
@ExtendWith(MockitoExtension.class)
public class RateLimiterTest {
    @Mock private StringRedisTemplate redis;                 // fake Redis
    @Mock private ValueOperations<String, String> valueOps;  // fake "the value part" of Redis
    @InjectMocks private RateLimiter rateLimiter;            // the REAL RateLimiter
```

There is **no Redis** here. Redis is fake. Only `RateLimiter` is real.

Two fakes are needed because the real code says `redis.opsForValue().increment(key)` — two steps, so two fakes.

### `setsExpiryOnlyOnce`

```java
when(valueOps.increment("k")).thenReturn(1L, 2L, 3L);   // first 1, then 2, then 3
rateLimiter.allow("k", 5, 60);
rateLimiter.allow("k", 5, 60);
rateLimiter.allow("k", 5, 60);
verify(redis, times(1)).expire(eq("k"), any(Duration.class));
```

`thenReturn(1L, 2L, 3L)` gives a different answer every time — like a real counter.

We call three times, but `expire` must be called **only one time**, on the first call. Why? Because `expire` says "delete this after 60 seconds". If we called it every time, the 60 seconds would start again and again, and the window would never finish.

### `blocksOverLimit`

```java
when(valueOps.increment("k")).thenReturn(6L);
assertFalse(rateLimiter.allow("k", 5, 60));
```

The counter says 6. The limit is 5. So the answer is `false` — stop.

### `failsOpen`

```java
when(redis.opsForValue()).thenThrow(new RedisConnectionFailureException("down"));
assertTrue(rateLimiter.allow("k", 5, 60));
```

Redis is broken. The answer is still **`true` — let the person in.**

This is on purpose. The rate limiter helps login. If a broken helper stopped login, nobody could enter the app. This really happened during development: Redis stopped and every login gave error 500.

---

## 5. OrderServiceTest — 2 tests

```java
@Mock private OrderRepository orderRepository;
@Mock private ProductRepository productRepository;
@Mock private UserRepository userRepository;

@InjectMocks private OrderService orderService;    // the real service
```

`@InjectMocks` builds the real `OrderService` and pushes the three fakes inside. It works because `OrderService` takes them in the constructor.

### `throwsWhenStockNotEnough`

```java
product.setStock(3);                                     // only 3 in the shop
...
new OrderItemRequest(productId, 5)                       // the person wants 5

assertThrows(InsufficientStockException.class, () -> orderService.create("test@example.com", request));
assertEquals(3, product.getStock());                     // the stock did not move
verify(orderRepository, never()).save(any());            // nothing was saved
```

Three checks, and all three are important:

1. the right error comes
2. the stock is still 3
3. **nothing was saved** — a bad order must leave nothing behind

The two `when(...)` lines are needed so the code can go past the first steps. Without the first one, the service stops earlier with "user not found", and we test the wrong thing.

### `createsOrderCorrectly`

```java
product.setPrice(new BigDecimal("25.50"));
product.setStock(10);
when(orderRepository.save(any(Order.class))).thenAnswer(i -> i.getArgument(0));
...
assertEquals(0, new BigDecimal("51.00").compareTo(response.totalPrice()));
assertEquals(8, product.getStock());
assertEquals(OrderStatus.PENDING, response.status());
```

**`thenAnswer(i -> i.getArgument(0))`** means: *give me back the same order I gave you*. A real repository does this. A fake gives `null`, and then the code breaks. This one line replaces the database.

**`compareTo` and not `assertEquals` for the money.** `BigDecimal.equals` also looks at the zeros: `51.0` and `51.00` are **not** equal for it. `compareTo` looks only at the number. Always use `compareTo` for money.

25.50 × 2 = 51.00. And 10 − 2 = 8.

---

## 6. OrderServiceIntegrationTest — 2 tests

```java
@SpringBootTest
@ActiveProfiles("test")
```

Now everything is real: a real service, a real H2 database, real transactions.

### `setUp` — before every test

```java
orderRepository.deleteAll();
productRepository.deleteAll();
tokenRepository.deleteAll();
userRepository.deleteAll();      // users LAST
```

**The order is important.** An order points to a user. A token points to a user. If you delete the user first, the database says no — the other lines still need it. So: children first, parents last.

Then it makes one user, and two products: a **laptop with stock 10** and a **mouse with stock 1**.

### `rollsBackEverythingWhenOneItemFails`

```java
new OrderItemRequest(laptopId, 2),    // OK, there are 10
new OrderItemRequest(mouseId, 5)      // bad, there is only 1

assertThrows(InsufficientStockException.class, ...);
assertEquals(10, productRepository.findById(laptopId).get().getStock());
assertEquals(0, orderRepository.count());
```

The order of the lines is not an accident. Line 1 works, line 2 fails.

Inside, the service already took 2 laptops (10 → 8). Then the mouse fails and it throws.

The test then reads the laptop **from the database** and it must be **10**, not 8. The transaction gave everything back.

And `count() == 0`: no half order stayed in the database.

### `reducesStockInDatabase`

The happy way. Buy 2 laptops:

- 1 order in the database
- laptop stock: 10 → 8
- total: 2 × 1000.00 = 2000.00

---

## 7. OrderControllerTest — 3 tests

```java
@WebMvcTest(OrderController.class)
@Import(SecurityConfig.class)
@AutoConfigureMockMvc(addFilters = false)
@WithMockUser
```

`@WebMvcTest` starts **only** the web part: the controller, the JSON, and `GlobalExceptionHandler`. No service, no database. So it is much faster than `@SpringBootTest`.

The other three lines are all necessary, and each one was a real problem:

- `@Import(SecurityConfig.class)` — `@WebMvcTest` does not take a normal `@Configuration` class. Without this line, Spring uses its own security and **every POST gives 403**.
- `@MockitoBean` for `OrderService`, `JwtService` and `AppUserDetailsService` — these are services, so the web slice does not build them. Without them the app does not start.
- `@WithMockUser` — `/api/v1/orders` needs a user. Without it: 403.

### `returns201`

```java
when(orderService.create(any(), any())).thenReturn(response);
...
.andExpect(status().isCreated())
.andExpect(jsonPath("$.totalPrice").value(51.00))
.andExpect(jsonPath("$.items[0].productName").value("Mouse"));
```

The service is fake and gives a ready answer. So this test does **not** test the order rules. It tests the web part: the code is 201, and the JSON has the right form.

`jsonPath("$.items[0].productName")` reads inside the JSON: `$` is the start, `items[0]` is the first line, `productName` is the field.

### `returns400`

```java
new OrderItemRequest(productId, 0)      // quantity 0
...
.andExpect(status().isBadRequest());
verify(orderService, never()).create(any(), any());
```

`OrderItemRequest` says `@Min(1)`. So 0 is not allowed.

The second line is the interesting one: the service was **never called**. Spring stopped the request before the controller. A fake service would take a 0 without a problem — the validation must catch it first.

### `returns409`

```java
when(orderService.create(any(), any())).thenThrow(new InsufficientStockException("Mouse", 1, 5));
...
.andExpect(status().isConflict());
```

We tell the fake to throw. Then we check that `GlobalExceptionHandler` makes **409** from it, and not 500.

---

## 8. OrderSecurityIntegrationTest — 3 tests

This file has no `setUp` and no database work. It asks only one question: **who can open which URL?**

### `requiresToken`

```java
mockMvc.perform(post("/api/v1/orders") ...)
       .andExpect(status().isUnauthorized());     // 401
```

No token → **401**. You are nobody.

### `userCannotDeleteProduct`

```java
@WithMockUser(roles = "USER")
mockMvc.perform(delete("/api/v1/products/" + UUID.randomUUID()))
       .andExpect(status().isForbidden());        // 403
```

A normal user → **403**. We know who you are, but you cannot do this.

**401 and 403 are different.** 401 = I do not know you. 403 = I know you, and the answer is no.

### `adminCanDeleteProduct`

```java
@WithMockUser(roles = "ADMIN")
mockMvc.perform(delete("/api/v1/products/" + UUID.randomUUID()))
       .andExpect(status().isNotFound());         // 404 !
```

Look: it waits for **404**, not 200.

This is correct. The id is random, so this product does not exist. But **404 is a good answer here**: it means security let the admin pass, and the code went to the database and did not find the product.

If the answer were 403, the admin could not pass. So 404 is exactly the proof we want.

---

## 9. AuthServiceIntegrationTest — 16 tests

The biggest file. It tests the whole account life: register, email, login, password.

### The setup

```java
@MockitoBean
private JavaMailSender mailSender;
```

A fake email sender. So the tests do **not** send real emails. The rest of the code still runs normally.

```java
@BeforeEach
void cleanUp() {
    resetTokenRepository.deleteAll();
    tokenRepository.deleteAll();
    userRepository.deleteAll();     // users last again
}
```

Every test uses the same email, `new1@example.com`, and some tests count the tokens. So everything must start empty.

Two small helpers:

```java
private User register() { ... }        // register and give back the user
private String onlyToken() { ... }     // take the first token
```

They make the tests short and easy to read.

### Register and email (6 tests)

| Test | It checks |
|---|---|
| `registerCreatesDisabledUser` | after register: `enabled = false`, and 1 token exists |
| `verifyEnablesUser` | after the link: `enabled = true`, and the token has `usedAt` |
| `verifyingTwiceIsSafe` | you can open the link 2 times. No error. |
| `verifyRetiresOtherTokens` | old tokens die when you verify |
| `unknownTokenIsRejected` | a token that does not exist → error |
| `expiredTokenIsRejected` | an old token → error, and the user stays `false` |

**Why is `verifyingTwiceIsSafe` important?** Gmail and Outlook **open the links in your email automatically**, to check if they are safe. So the token is often already used when the person clicks. Without this rule, a normal user would see "link already used" for an account that is already fine.

**How does `expiredTokenIsRejected` make an old token?** It cannot wait 24 hours. So it changes the date directly:

```java
token.setExpiresAt(LocalDateTime.now().minusMinutes(1));   // one minute in the past
tokenRepository.save(token);
```

### Login (3 tests)

**`registrationIgnoresAClientSuppliedRole`** — the most important test for security:

```java
.content("""
    {"firstName":"Sneaky","lastName":"User","email":"%s",
     "password":"Password123","role":"ADMIN","enabled":true}
    """.formatted(EMAIL))
.andExpect(status().isCreated());

assertEquals(Role.USER, created.getRole());
assertFalse(created.isEnabled());
```

Somebody sends `"role":"ADMIN"` and `"enabled":true`. The answer is 201 — **but the user is `USER` and `enabled = false`.**

`UserCreateRequest` has no `role` and no `enabled` field, so Jackson throws these two words away. If one day somebody puts `role` back in that class, this test becomes red.

**`disabledUserCannotLogin`** — a user who did not click the link gets `DisabledException`.

**`unverifiedLoginIsDistinguishedFromBadCredentials`** — two different messages:

```java
// right password, but not verified
.andExpect(jsonPath("$.message").value("Account not verified"));

// email that does not exist
.andExpect(jsonPath("$.message").value("invalid email or password"));
```

The first message helps the user. Without it, somebody with a correct password looks for a mistake in the password — and the password was fine.

The second message stays not clear, so a stranger cannot learn which emails have an account.

### Password reset (5 tests)

| Test | It checks |
|---|---|
| `resetIsOnlyForVerifiedAccounts` | a user who did not verify gets **no** token |
| `resetIsSilentForUnknownAddress` | an email that does not exist: no error, no token |
| `resetChangesThePassword` | the new password works, the old one does not |
| `resetTokenIsSingleUse` | the second try fails |
| `expiredResetTokenIsRejected` | after 1 hour it does not work |

**`resetChangesThePassword`** tests both sides:

```java
assertNotNull(authService.login(new LoginRequest(EMAIL, "BrandNewPass9")));    // new: OK
assertThrows(BadCredentialsException.class,
        () -> authService.login(new LoginRequest(EMAIL, "password123")));      // old: no
```

The second line is important. Without it, the code could save the new password and keep the old one too.

**Why `resetTokenIsSingleUse` and not "twice is safe"?** This is the opposite of the email link, and it is on purpose:

- **email link**: open it 2 times → OK. The result is the same both times: the account is verified.
- **reset token**: use it 2 times → error. This token can **change the password**. If somebody sees your email later, they must not be able to use it again.

**Why `resetIsOnlyForVerifiedAccounts`?** Without this rule, a bad person could:

1. register with **your** email
2. never open the email
3. use "forgot password"
4. now they have a working password on your address

The test makes sure the token count is 0.

---

## Where do the tests save data?

**Not in your PostgreSQL.** The file `src/test/resources/application-test.properties` says:

```properties
spring.datasource.url=jdbc:h2:mem:testdb;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;NON_KEYWORDS=USER
spring.jpa.hibernate.ddl-auto=create-drop
```

| Part | What it means |
|---|---|
| `h2:mem:testdb` | a small database **in the memory**. It dies with the tests. |
| `MODE=PostgreSQL` | H2 speaks like PostgreSQL, so the SQL is the same as in production |
| `DB_CLOSE_DELAY=-1` | keep the database alive between the tests. Without this it disappears too early. |
| `NON_KEYWORDS=USER` | `USER` is a special word in H2. This line makes it normal again. |
| `create-drop` | build the tables at the start, delete them at the end. Every run starts clean. |

The tests with `@SpringBootTest` **share one database**. This is why `OrderServiceIntegrationTest` must also delete the tokens: `AuthServiceIntegrationTest` leaves some there, and they point to users.
