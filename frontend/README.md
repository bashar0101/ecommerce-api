# Shop — front end

Next.js front end for the Spring Boot e-commerce API. Every endpoint the backend
has is reachable from a page here.

Built with Next.js 16 (App Router), React 19, TypeScript and Tailwind 4.

---

## Run it

```bash
npm install
npm run dev
```

Open <http://localhost:3000>.

By default it talks to the deployed API at
`https://ecommerce-api-5gra.onrender.com`. To use a local backend, copy the
example file and change the URL:

```bash
cp .env.local.example .env.local
# NEXT_PUBLIC_API_URL=http://localhost:8080
```

> The free Render instance sleeps after 15 minutes with no traffic. The first
> request can take about 50 seconds. The pages say so while they wait.

---

## The pages

| Page | What it does | API it calls |
|---|---|---|
| `/` | Product list, paged | `GET /products` |
| `/products/[id]` | One product, choose a quantity | `GET /products/{id}` |
| `/cart` | Change quantities, place the order | `POST /orders` |
| `/orders` | Your past orders | `GET /orders` |
| `/register` | Create an account | `POST /auth/register` |
| `/login` | Get a token | `POST /auth/login` |
| `/verify?token=...` | Opened from the activation email | `GET /auth/verify` |
| `/resend` | Send the activation email again | `POST /auth/resend` |
| `/forgot-password` | Ask for a reset email | `POST /auth/forgot-password` |
| `/reset-password` | Set a new password with the token | `POST /auth/reset-password` |
| `/admin/products` | Create, edit, delete products | `POST` / `PUT` / `DELETE /products` |

---

## How it is put together

```
src/
  lib/
    types.ts        the API shapes, in one place
    api.ts          one fetch wrapper; adds the token, turns errors into ApiError
    localStore.ts   localStorage through useSyncExternalStore
    auth.tsx        AuthProvider + useAuth
    cart.tsx        CartProvider + useCart
  components/
    ui.tsx          Field, Button, Alert, ErrorBox, Card, money
    Nav.tsx         the menu
  app/              one folder per page
```

### Errors come from the server, not from us

`GlobalExceptionHandler` on the backend returns the same body every time:

```json
{ "status": 400, "message": "Not valid", "fields": { "password": "..." } }
```

`api.ts` turns that into an `ApiError` carrying `message` and `fields`, and
`<ErrorBox>` prints both. So "Password must be between 8 and 72 characters"
appears without the front end owning a copy of that rule.

The password hints on the forms **are** duplicated, on purpose — a user should
see the rules before pressing the button. The server stays the authority.

### The cart is only in your browser

The API has no cart. `POST /orders` takes the whole basket in one request and
checks all the stock inside one transaction. So the cart is `localStorage` until
you order, and then it is cleared.

### The token

Kept in `localStorage` under `ecommerce.token`, and `api.ts` adds
`Authorization: Bearer ...` to every call.

`auth.tsx` decodes the payload to read the role, **without checking the
signature**. That only decides whether the Admin link is shown. Every real check
happens on the server, which does verify. If a token is expired or revoked, the
server answers 401 and `api.ts` deletes it.

> `localStorage` is readable by any script on the page, so a cross-site
> scripting bug would expose the token. An httpOnly cookie is safer, but the
> backend does not set one today.

### Why `useSyncExternalStore`

Reading `localStorage` inside `useEffect` and calling `setState` renders once
with the wrong value and again with the right one. React 19 and Next 16 flag it.
`localStore.ts` uses `useSyncExternalStore` instead: `getServerSnapshot` returns
the empty value during SSR, then React switches to the real one after hydration.
It also listens for `storage` events, so logging out in one tab updates the
others.

---

## Being an admin

`/admin/products` needs a token whose `role` is `ADMIN`. Registration always
creates a `USER` — the server refuses to let the client choose. Promote yourself
in the database:

```sql
UPDATE users SET role = 'ADMIN' WHERE email = 'you@example.com';
```

Then **log out and log in again**: the role is copied into the token at login,
so an older token still says `USER`.

---

## Checks

```bash
npm run lint     # clean
npm run build    # 12 routes
```

There are no automated tests here yet. The backend has 39.
