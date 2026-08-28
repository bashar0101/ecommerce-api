# Configuration — which file does what

Five files decide how this app connects to anything. They overlap, which is what makes them confusing. This explains what each one is for, and — for the database, mail and Redis — why the same setting has a *different value* in each place.

The short version:

| File | Committed? | Read by | Purpose |
|---|---|---|---|
| `application.properties` | yes | the app | The only file the app reads. Declares every setting and its **default**. |
| `.env` | **no** (gitignored) | the app, on your machine | Your real local secrets. Overrides defaults. |
| `.env.example` | yes | **nobody** | A template for humans. Copy it to `.env`. Has no effect on anything. |
| `compose.yaml` | yes | Docker | Runs Postgres, Redis and Mailpit locally, and optionally the app. |
| `render.yaml` | yes | Render | Creates the production database, Redis and web service. |

---

## The one rule that explains everything

`application.properties` never hardcodes a value. Every setting is written as:

```properties
spring.datasource.url=jdbc:postgresql://${DB_HOST:localhost}:${DB_PORT:5432}/${DB_NAME:ecommerce}
```

`${DB_HOST:localhost}` means **"use the environment variable `DB_HOST`; if it is not set, use `localhost`."**

So there is exactly one question to ask about any setting: **who sets that environment variable in this situation?**

```
                        DB_HOST comes from...
running ./mvnw          .env, or the default "localhost"
docker compose up       compose.yaml's app service  ->  "db"
deployed on Render      render.yaml, from the managed database  ->  a Render hostname
```

Same code, same jar, three different values. Nothing is duplicated — each file answers the question for one environment.

---

## `application.properties` — the only file the app reads

Lives in `src/main/resources/`, ships inside the jar.

It does two things:

1. **Declares every setting** the app has — datasource, JPA, JWT, mail, Redis.
2. **Provides a default for each**, chosen so that a plain `./mvnw spring-boot:run` works against local Postgres and the Mailpit container with no `.env` at all.

One line makes `.env` work:

```properties
spring.config.import=optional:file:.env[.properties]
```

That tells Spring to also read `.env` from the project root. `optional:` means the app still starts when the file is missing — which is exactly the case on Render, where the values arrive as real environment variables instead.

---

## `.env` — your local secrets, never committed

Gitignored. It currently holds only two keys:

```properties
DB_PASSWORD=...
JWT_SECRET=...
```

**Why only two?** Because every other default in `application.properties` is already correct for local development. `DB_HOST` defaults to `localhost`, `MAIL_PORT` to `1025`, and so on. You only put a key in `.env` when your machine differs from the default, or when it is a secret that must not be committed.

`DB_PASSWORD` has no default on purpose — the app fails to start without it, rather than silently trying a blank password.

---

## `.env.example` — a template, read by nobody

This is the file that causes the most confusion, so to be explicit:

> **`.env.example` is never loaded. Not by the app, not by Docker, not by Render.** It is documentation with a `.env`-shaped filename.

Its only job is to tell a new developer — or you, on a new machine — which keys exist and what a plausible value looks like. You copy it to `.env` and edit.

That is why it lists keys your real `.env` does not have. It is showing you the full menu, not the settings in force.

---

## `compose.yaml` — the local supporting cast

Runs four services. Three of them are things your app talks to; the fourth is the app itself, which you usually **do not** run this way during development.

| Service | Image | Host port | What it is for |
|---|---|---|---|
| `db` | postgres:16 | **5433** | Local Postgres |
| `redis` | redis:7-alpine | 6379 | Rate limiter + product cache |
| `mail` | axllent/mailpit | 1025 SMTP, 8025 web | Fake inbox at <http://localhost:8025> |
| `app` | built from `Dockerfile` | 8080 | The app, containerised |

Day to day you run only the supporting services and start the app from your IDE:

```bash
docker compose up -d db redis mail
./mvnw spring-boot:run
```

---

## The part that trips everyone up: two networks, two sets of addresses

A container has its own `localhost`. Inside the `app` container, `localhost` means *the app itself* — not your machine, and not the other containers.

So the same database has **two addresses**, and which one is right depends on where the code asking is running:

```
        YOUR MACHINE                    DOCKER NETWORK
   +---------------------+        +--------------------------+
   |  ./mvnw spring-boot |        |  app container           |
   |         |           |        |        |                 |
   |         v           |        |        v                 |
   |   localhost:5433 ---+------->|    db:5432               |
   |   localhost:6379 ---+------->|    redis:6379            |
   |   localhost:1025 ---+------->|    mail:1025             |
   +---------------------+        +--------------------------+
        published ports              service names
```

**Postgres is the clearest case.** The container always listens on **5432** internally. `compose.yaml` publishes it to your machine as **5433**:

```yaml
ports:
  - "5433:5432"     # host:container
```

5433 was chosen so it cannot collide with a PostgreSQL you already have installed on 5432. So:

- from your machine (psql, DBeaver, `./mvnw`) → **`localhost:5433`**
- from inside compose (the `app` container) → **`db:5432`**

Both are the same database. Neither value is wrong; they are answers to different questions.

This is why `compose.yaml` sets these on the `app` service — the defaults in `application.properties` are the *host* answers, and a container needs the *network* answers:

```yaml
DB_HOST: db          # not localhost
DB_PORT: 5432        # not 5433
REDIS_HOST: redis    # not localhost
MAIL_HOST: mail      # not localhost
```

### The trap

Putting `REDIS_HOST=redis` (or `DB_HOST=db`) in `.env` breaks running the app with `./mvnw`, because those names only resolve inside the Docker network. Your machine has no idea what `redis` means, and the failure is slow and unhelpful: the OS spends **6-10 seconds** failing to resolve the name before anything gives up, so every login hangs and rate limiting silently stops working.

> **Rule: service names (`db`, `redis`, `mail`) belong in `compose.yaml`. `localhost` belongs in `.env`.**

**Note:** `.env.example` currently has `REDIS_HOST=redis`, which contradicts its own comment three lines above it. Copy that file to `.env` as-is and you get the 6-10 second hang. It should be `localhost`.

### One more: `APP_BASE_URL` stays `localhost` even inside Docker

```yaml
APP_BASE_URL: http://localhost:8080     # in compose.yaml, on the app service
```

That looks like it breaks the rule above, and it does not. This value goes into the **activation links inside emails**, which are opened by a browser on your machine — not by anything inside the Docker network. It has to be an address *the reader* can reach.

---

## `render.yaml` — the production blueprint

Render reads this once to create three things: a Postgres database (`ecommerce-db`), a Redis instance (`ecommerce-redis`), and the web service (`ecommerce-api`).

It fills the same environment variables, from three different sources.

**Wired automatically** — you never see or type these:

```yaml
- key: DB_HOST
  fromDatabase: { name: ecommerce-db, property: host }
- key: REDIS_HOST
  fromService: { type: keyvalue, name: ecommerce-redis, property: host }
```

**Generated** — a 256-bit random value, created once:

```yaml
- key: JWT_SECRET
  generateValue: true
```

**Prompted** — `sync: false` means Render asks you at deploy time and stores it outside git:

```yaml
- key: MAIL_PASSWORD
  sync: false
```

The database credentials are taken as **separate parts** rather than one URL on purpose: Render publishes a `postgres://user:pass@host/db` connection string, and the JDBC driver needs `jdbc:postgresql://`. Taking host, port and name individually avoids parsing and rewriting it.

---

## Mail: two implementations, one interface

`MailService` is an interface with two implementations, and `app.mail.provider` picks which bean exists:

| Provider | Class | Transport | Used where |
|---|---|---|---|
| `smtp` (default) | `SmtpMailService` | SMTP to `MAIL_HOST:MAIL_PORT` | locally, against Mailpit |
| `resend` | `ResendMailService` | HTTPS POST to `api.resend.com` | Render |

The split exists because **Render blocks outbound SMTP** (ports 25, 465, 587). The block drops packets instead of refusing them, so an SMTP send there does not fail quickly — it hangs for the full socket timeout and then reports `Connect timed out`. Port 443 is never blocked, so the HTTP API sidesteps it.

`UserRegisteredListener` calls `mailService.send(...)` and never knows which one answered.

---

## Worked example: where does `DB_PASSWORD` come from?

| Situation | Source | Value |
|---|---|---|
| `./mvnw spring-boot:run` | `.env` | your local Postgres password |
| `docker compose up` | `compose.yaml`, app service | `ecommercepass` |
| Render | `render.yaml` → `fromDatabase` | generated by Render, never seen |

Three values, one property, zero duplication. Each file answers for its own environment.

---

## Quick answers

**"I changed a setting and nothing happened."**
Did you edit `.env.example`? Nothing reads it. Edit `.env`.

**"Which Postgres am I connected to?"**
Port 5432 is the one installed on your machine; 5433 is the container. They are different databases with the same schema, which is easy to mix up. `.env` currently sets no `DB_PORT`, so the app uses the default — **5432, the native one**.

**"Why does `.env` have so few keys?"**
Because the defaults in `application.properties` already cover local development. Add a key only to override a default or to hold a secret.

**"Do I need `.env` on Render?"**
No. It is gitignored, so it never gets there. `render.yaml` supplies the same keys as real environment variables, and `optional:` in the config import means the app does not care that the file is absent.
