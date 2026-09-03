# E-Commerce API

This is a learning project. It is a REST API for a small online shop. It is built with Spring Boot.

What it can do:

- People make an account. They get an email. They click a link. Now the account works.
- They log in. They get a token. They send this token with every request.
- Everybody can see the products. Only an admin can add or change or delete a product.
- A user can make an order. The app takes the products from the stock.
- If somebody forgets the password, they can ask for a new one by email.

---

## Contents

- [What you need](#what-you-need)
- [How to run it](#how-to-run-it)
- [The main idea: four layers](#the-main-idea-four-layers)
- [The files](#the-files)
- [The database tables](#the-database-tables)
- [All the endpoints](#all-the-endpoints)
- [How it works, step by step](#how-it-works-step-by-step)
  - [1. Make an account](#1-make-an-account)
  - [2. Log in](#2-log-in)
  - [3. Send a request with a token](#3-send-a-request-with-a-token)
  - [4. Make an order](#4-make-an-order)
  - [5. Forgot the password](#5-forgot-the-password)
- [Redis: faster and safer](#redis-faster-and-safer)
- [Errors](#errors)
- [Settings](#settings)
- [Tests](#tests)
- [Put it online with Render](#put-it-online-with-render)
- [Problems we know about](#problems-we-know-about)

---

## What you need

| Thing | Why |
|---|---|
| Java 17 | to run the app |
| PostgreSQL | to save users, products and orders |
| Docker | it gives you Redis and a fake email box |
| Maven | it builds the app. Use `./mvnw`, you do not install it |

Other parts:

- **Spring Boot 4.1.0** — the framework
- **JWT** — the login token
- **Redis** — it counts login tries, and it remembers products
- **Mailpit** — a fake email box on your computer. You read the emails in the browser.
- **Resend** — a real email company. We use it only when the app is online.

---

## How to run it

**Step 1.** Make the database:

```bash
createdb -U postgres ecommerce
```

**Step 2.** Make a file `.env` in the main folder. Put two lines in it:

```properties
DB_PASSWORD=your_postgres_password
JWT_SECRET=any_long_random_text_32_letters_or_more
```

`JWT_SECRET` must be 32 letters or more. If it is shorter, the app does not start.

**Step 3.** Start Redis and the email box:

```bash
docker compose up -d db redis mail
```

**Step 4.** Start the app:

```bash
./mvnw spring-boot:run
```

The app runs on <http://localhost:8080>.
You read the emails on <http://localhost:8025>.

There is one more file, `CONFIGURATION.md`. It explains `.env`, `.env.example`, `compose.yaml` and `render.yaml`. Read it if you are not sure which file does what.

---

## The main idea: four layers

Every request goes down. Every answer comes up. One layer talks only to the next layer.

```
   1. Security      Is this person real? Can they do this?
        |
        v
   2. Controller    It reads the HTTP request. It gives back the answer.
        |
        v
   3. Service       Here are the rules. This is the important part.
        |
        v
   4. Repository    It talks to the database.
        |
        v
      PostgreSQL
```

Two rules we always follow:

1. **The controller does not think.** It only takes the request and calls a service.
2. **Entities stay inside.** We never send a `User` object to the client. A `User` has the password inside it. We send a DTO instead.

---

## The files

```
com.apps.ecommerce
├── config/       settings and small helpers
├── controller/   the HTTP part
├── dto/          the objects we send and receive
├── entity/       the database tables
├── enums/        fixed lists (USER/ADMIN, PENDING/PAID/...)
├── exception/    our errors
├── repository/   database questions
├── security/     JWT and login
└── service/      the rules
```

### The most important classes

| Class | What it does |
|---|---|
| `AuthService` | register, login, verify email, reset password |
| `OrderService` | make an order, check the stock, count the price |
| `ProductService` | add, change, delete, find products |
| `JwtService` | makes the token, and reads the token |
| `JwtAuthFilter` | looks at every request and finds who you are |
| `AppUserDetailsService` | takes your `User` and gives it to Spring Security |
| `SecurityConfig` | says which URL needs which right |
| `RateLimiter` | counts your login tries in Redis |
| `MailService` | sends an email. Two versions: SMTP or Resend. |
| `GlobalExceptionHandler` | turns every error into the same JSON |

---

## The database tables

**users**

| Column | Note |
|---|---|
| id | UUID |
| firstName, lastName | |
| email | must be different for every user |
| password | never the real password. Only a BCrypt hash. |
| role | `USER` or `ADMIN` |
| enabled | `false` until the person clicks the email link |
| createdAt | |

The table is called `users`, not `user`. `USER` is a special word in SQL, so it does not work.

**product** — id, name, price, stock, description

**orders** — id, user, status, totalPrice, createdAt, and a list of items.
The table is `orders`, not `order`. Again a special word in SQL.

**order_item** — one line of one order: the product, how many, and the price.
We copy the price here. So if the shop changes the price tomorrow, the old order does not change.

**verification_token** — the email link. It is good for 24 hours.

**password_reset_token** — the password link. It is good for **1 hour**.

Why only 1 hour? Because this link can change the password. If somebody steals it, they take the account. So it must die fast.

---

## All the endpoints

### Everybody can use these

| Method | URL | What it does |
|---|---|---|
| POST | `/api/v1/auth/register` | make an account |
| POST | `/api/v1/auth/login` | get a token |
| GET | `/api/v1/auth/verify?token=...` | make the account work |
| POST | `/api/v1/auth/resend` | send the email again |
| POST | `/api/v1/auth/forgot-password` | ask for a new password |
| POST | `/api/v1/auth/reset-password` | put the new password |
| GET | `/api/v1/products` | see all products (with pages) |
| GET | `/api/v1/products/{id}` | see one product |

### You need a token

| Method | URL | What it does |
|---|---|---|
| POST | `/api/v1/orders` | make an order |
| GET | `/api/v1/orders` | see my orders |

### You need to be ADMIN

| Method | URL |
|---|---|
| POST | `/api/v1/products` |
| PUT | `/api/v1/products/{id}` |
| DELETE | `/api/v1/products/{id}` |

Send the token like this:

```
Authorization: Bearer eyJhbGciOiJIUzI1NiJ9...
```

---

## How it works, step by step

### 1. Make an account

You send:

```json
POST /api/v1/auth/register
{
  "firstName": "Bashar",
  "lastName": "Khoujah",
  "email": "me@example.com",
  "password": "Password123"
}
```

The password must be:

- 8 to 72 letters
- one small letter, one big letter, one number
- no spaces

Why 72? BCrypt cuts everything after 72. So a longer password is a lie.

**There is no `role` here.** You cannot choose it. The server always gives `USER`. If you could choose, everybody could become an admin.

Now the app does this:

```
1. Is this email already here?        -> yes: error 409
2. Save the user. enabled = false.    <- important
3. Save a token. It dies in 24 hours.
4. Say "somebody registered"          <- only a message, no email yet
5. Answer 201 to the client
```

The email goes **after**. A different thread sends it.

Why? Two reasons:

- If the database fails at the end, we do not send an email for a user who does not exist.
- Email is slow. The client should not wait for it.

The class `UserRegisteredListener` does this job.

### The link in the email

```
http://localhost:8080/api/v1/auth/verify?token=1a2b3c...
```

You open it. Now `enabled = true`. Now you can log in.

Before that, login gives:

```json
{"status": 401, "message": "Account not verified"}
```

Not "wrong password". The password was correct. Only the email was not confirmed.

### 2. Log in

```json
POST /api/v1/auth/login
{"email": "me@example.com", "password": "Password123"}
```

You get:

```json
{"token": "eyJhbGciOiJIUzI1NiJ9..."}
```

Inside the token: your email, your id, your role, and the end time. It is good for **24 hours**.

**Only 5 tries in 60 seconds.** Try 6 times and you get error 429. Redis counts this.

### 3. Send a request with a token

```
GET /api/v1/orders
Authorization: Bearer eyJ...
```

`JwtAuthFilter` looks at every request:

```
1. Is there an "Authorization: Bearer ..." header?   no -> continue as a guest
2. Is the token real? Is it still alive?             no -> continue as a guest
3. Read the user from the database
4. Is the user still enabled?                        no -> continue as a guest
5. Now Spring knows who you are
```

Then `SecurityConfig` decides:

- guest + private URL → **401**
- user + admin URL → **403**
- everything OK → the controller runs

Step 4 is useful. If you set `enabled = false` for somebody, their token stops working now. You do not wait 24 hours.

### 4. Make an order

```json
POST /api/v1/orders
Authorization: Bearer eyJ...
{
  "items": [
    {"productId": "...", "quantity": 2}
  ]
}
```

**We take your email from the token.** You do not send it. So nobody can order for another person.

What `OrderService` does:

```
1. Find the user by email                    -> not there: 404
2. Put all product ids in a Set              (no doubles)
3. Ask the database ONE time for all products
4. One product missing?                      -> 404, and we stop here
5. Make the order: status = PENDING, total = 0
6. For every line:
     is the stock enough?  no  -> 409, and everything goes back
     take from the stock
     make an order_item, copy the price now
     total = total + price * quantity
7. Save one time. The lines are saved too.
```

Two important things:

**Only one question to the database in step 3.** If we asked one time for every product, 20 products would be 20 questions. This is called the N+1 problem.

**Everything or nothing.** The method is `@Transactional`. If line 3 has no stock, lines 1 and 2 get their stock back. You never have half an order.

Money is always `BigDecimal`, never `double`. `double` cannot save `0.1` correctly, and after some time the numbers are wrong.

### 5. Forgot the password

```json
POST /api/v1/auth/forgot-password
{"email": "me@example.com"}
```

The answer is **always the same**:

```json
{"message": "If that address has an account, a reset email has been sent"}
```

The email does not exist? Same answer. Not verified? Same answer. Why? Because a different answer tells a stranger which emails have an account here.

Then you send the token from the email:

```json
POST /api/v1/auth/reset-password
{"token": "...", "newPassword": "BrandNewPass9"}
```

The new password has the same rules as the first one.

**One token, one time.** If you send it again, you get an error. This is different from the email link, where two clicks are OK. A reset token can take the account, so nobody may use it two times.

**No reset for a user who is not verified.** If we allowed it, somebody could write your email, never open the mail, then "reset" and get a working password on your address.

---

## Redis: faster and safer

Redis does two jobs:

1. **It counts login tries.** 5 in 60 seconds. Number 6 gets 429.
2. **It remembers products.** `findById` keeps the answer for 10 minutes.

**If Redis is down, the app still works.** This is on purpose:

- `RateLimiter` catches the error and says "yes, you can". You lose the counting, but people can still log in.
- `CacheConfig` has a `CacheErrorHandler`. It writes a warning and goes to the database.

Why? Redis is a helper. A helper that is sick must not stop the whole app.

---

## Errors

Every error looks the same:

```json
{
  "status": 400,
  "message": "Not valid",
  "error": "...",
  "timestamp": "2026-08-29T12:00:00",
  "fields": { "password": "Password must be between 8 and 72 characters" }
}
```

`fields` is only there for form errors.

| When | Code |
|---|---|
| bad data in the body | 400 |
| bad JSON, or a wrong value | 400 |
| bad or old token | 400 |
| account not verified | 401 |
| wrong email or password | 401 |
| no rights (not admin) | 403 |
| not found | 404 |
| email already used | 409 |
| not enough stock | 409 |
| too many tries | 429 |
| something else | 500 |

---

## Settings

Everything comes from the outside. `application.properties` only has default values:

```properties
spring.datasource.url=jdbc:postgresql://${DB_HOST:localhost}:${DB_PORT:5432}/${DB_NAME:ecommerce}
```

`${DB_HOST:localhost}` means: take `DB_HOST` from the environment. If it is not there, use `localhost`.

So the same code works in three places:

| Where | Who gives the values |
|---|---|
| your computer | `.env`, or the default |
| docker compose | `compose.yaml` |
| Render | `render.yaml` |

Full explanation in `CONFIGURATION.md`.

---

## Tests

```bash
./mvnw test
```

**34 tests. All green.**

The tests use H2, a small database inside the memory. They never touch your PostgreSQL.

| File | How many | What it tests |
|---|---|---|
| `EcommerceApplicationTests` | 1 | the app starts |
| `ProductTest`, `OrderTest` | 4 | `equals` and `hashCode` |
| `RateLimiterTest` | 3 | the counting |
| `OrderServiceTest` | 2 | the rules, with fake repositories |
| `OrderServiceIntegrationTest` | 2 | a real order, and everything goes back |
| `OrderControllerTest` | 3 | 201, 400, 409 |
| `OrderSecurityIntegrationTest` | 3 | 401 and 403 |
| `AuthServiceIntegrationTest` | 16 | register, verify, login, reset password |

**Why two kinds of test for the same service?**

`OrderServiceTest` uses fake repositories. It is fast. But a fake has no transaction. So "everything goes back" cannot be tested there — the Java object stays changed.

`OrderServiceIntegrationTest` uses a real database. Only there you can see the stock go from 8 back to 10.

---

## Put it online with Render

The file `render.yaml` makes three things: a PostgreSQL database, a Redis, and the app.

1. Push your code to GitHub.
2. In Render: **New → Blueprint**. Choose your repository.
3. Render asks you for some values. Write them.
4. Wait for the build (3-6 minutes).
5. Copy your URL. Put it in `APP_BASE_URL`. Save.

You must write these:

| Key | Value |
|---|---|
| `MAIL_PROVIDER` | `resend` |
| `RESEND_API_KEY` | your key from resend.com |
| `MAIL_FROM` | `onboarding@resend.dev` |
| `APP_BASE_URL` | your Render URL |

**Why Resend and not normal email?** Render does not let the app open the email ports (25, 465, 587). The connection waits and then dies. Resend has a normal web address (port 443), and that always works.

**Small things about the free plan:**

- After 15 minutes with no visitors, the app sleeps. The next request takes about 50 seconds.
- The free database is deleted after 30 days.
- Resend sends only to your own email address, until you verify a domain.

**To make an admin**, change it directly in the database:

```sql
UPDATE users SET role = 'ADMIN' WHERE email = 'you@example.com';
```

Then log in again. The role is inside the token, so an old token still says `USER`.

---

## Problems we know about

We know these things. They are not finished.

**Old tokens do not die.** You log in again and get a new token — but the old one still works until 24 hours are over. A JWT is not saved on the server, so we cannot delete it. Even a new password does not stop it.

The answer for this is a *refresh token*: a short token (15 minutes) for the requests, and a long token in the database for asking a new one. Then you can really log somebody out. It is not built yet.

Today the only way to stop somebody is `enabled = false`. `JwtAuthFilter` checks this on every request.

**A failed email is lost.** If the email does not go out, the app only writes it in the log. The user waits and nothing comes. There is no second try.

**Two people can buy the last item.** Both read `stock = 1`, both say OK, both take it. Now the stock is `-1`. To fix this we need a lock in the database, or a `@Version` column.

**Not built yet:** change the order status (PENDING → PAID → SHIPPED), cancel an order and give the stock back, see one order by id.

**Old code inside:** `UserService`, `UserController` and `DataSeeder` do nothing now. `OrderService` still has an old version of `create` inside a comment.
