import type {
  ApiErrorBody, Order, Page, Product, ProductInput, UserResponse,
} from "./types";

const BASE =
  process.env.NEXT_PUBLIC_API_URL ?? "https://ecommerce-api-5gra.onrender.com";

const TOKEN_KEY = "ecommerce.token";

/** Read straight from storage rather than through React: this runs inside
 *  fetch calls, where no hook is available. lib/auth.tsx owns writing it. */
export function getToken(): string | null {
  if (typeof window === "undefined") return null;
  try {
    const raw = window.localStorage.getItem(TOKEN_KEY);
    // The store writes JSON, so a plain string arrives quoted.
    return raw ? (JSON.parse(raw) as string) : null;
  } catch {
    return null; // private mode, blocked or corrupt storage
  }
}

export function clearToken() {
  try {
    window.localStorage.removeItem(TOKEN_KEY);
    // The store in localStore.ts listens for this, so the menu updates at once.
    // A real cross-tab write fires it by itself; this covers the same-tab case.
    window.dispatchEvent(new StorageEvent("storage", { key: TOKEN_KEY }));
  } catch {
    /* ignore */
  }
}

/** Carries the server's own message so the UI never has to invent one. */
export class ApiError extends Error {
  constructor(
    public status: number,
    message: string,
    public fields: Record<string, string> | null = null,
  ) {
    super(message);
  }
}

async function request<T>(path: string, init: RequestInit = {}): Promise<T> {
  const token = getToken();
  const headers = new Headers(init.headers);
  if (init.body) headers.set("Content-Type", "application/json");
  if (token) headers.set("Authorization", `Bearer ${token}`);

  let res: Response;
  try {
    res = await fetch(`${BASE}${path}`, { ...init, headers });
  } catch {
    // Render's free tier sleeps after 15 minutes; the first call can take ~50s.
    throw new ApiError(0, "Cannot reach the server. It may be waking up - try again in a moment.");
  }

  if (res.status === 204) return undefined as T;

  const text = await res.text();
  const body = text ? (JSON.parse(text) as unknown) : null;

  if (!res.ok) {
    // 401 while holding a token means it is expired, revoked, or signed with an
    // old secret. Drop it so the UI stops claiming to be logged in.
    if (res.status === 401 && token) clearToken();

    const e = body as ApiErrorBody | null;
    throw new ApiError(
      res.status,
      e?.message ?? `Request failed (${res.status})`,
      e?.fields ?? null,
    );
  }
  return body as T;
}

const json = (data: unknown) => JSON.stringify(data);

export const api = {
  // ---- auth (all public) ----
  register: (d: { firstName: string; lastName: string; email: string; password: string }) =>
    request<UserResponse>("/api/v1/auth/register", { method: "POST", body: json(d) }),

  login: (d: { email: string; password: string }) =>
    request<{ token: string }>("/api/v1/auth/login", { method: "POST", body: json(d) }),

  verify: (token: string) =>
    request<{ message: string }>(`/api/v1/auth/verify?token=${encodeURIComponent(token)}`),

  resendVerification: (email: string) =>
    request<{ message: string }>("/api/v1/auth/resend", { method: "POST", body: json({ email }) }),

  forgotPassword: (email: string) =>
    request<{ message: string }>("/api/v1/auth/forgot-password", {
      method: "POST", body: json({ email }),
    }),

  resetPassword: (d: { token: string; newPassword: string }) =>
    request<{ message: string }>("/api/v1/auth/reset-password", {
      method: "POST", body: json(d),
    }),

  // ---- products: reading is public, writing needs ADMIN ----
  products: (page = 0, size = 12) =>
    request<Page<Product>>(`/api/v1/products?page=${page}&size=${size}&sort=name`),

  product: (id: string) => request<Product>(`/api/v1/products/${id}`),

  createProduct: (d: ProductInput) =>
    request<Product>("/api/v1/products", { method: "POST", body: json(d) }),

  updateProduct: (id: string, d: ProductInput) =>
    request<Product>(`/api/v1/products/${id}`, { method: "PUT", body: json(d) }),

  deleteProduct: (id: string) =>
    request<void>(`/api/v1/products/${id}`, { method: "DELETE" }),

  // ---- orders: need a token ----
  createOrder: (items: { productId: string; quantity: number }[]) =>
    request<Order>("/api/v1/orders", { method: "POST", body: json({ items }) }),

  myOrders: () => request<Order[]>("/api/v1/orders"),
};
