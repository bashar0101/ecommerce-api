"use client";

import { createContext, useCallback, useContext, useMemo } from "react";
import { api } from "./api";
import { createLocalStore, useHydrated, useStore } from "./localStore";
import type { Role } from "./types";

interface Session {
  email: string;
  userId: string;
  role: Role;
  exp: number;
}

/** The raw JWT string, shared with lib/api.ts through the same storage key. */
const tokenStore = createLocalStore<string | null>("ecommerce.token", null);

export const readToken = () => {
  try {
    return tokenStore.get();
  } catch {
    return null;
  }
};

/**
 * Reads the payload WITHOUT verifying the signature. That is fine here: this
 * only decides what the menu shows. Every real permission check happens on the
 * server, which does verify. Never trust this for anything that matters.
 */
function decode(token: string): Session | null {
  try {
    const padded = token.split(".")[1].replace(/-/g, "+").replace(/_/g, "/");
    const payload = JSON.parse(atob(padded + "=".repeat((4 - (padded.length % 4)) % 4)));
    return { email: payload.sub, userId: payload.userId, role: payload.role, exp: payload.exp };
  } catch {
    return null;
  }
}

interface AuthValue {
  session: Session | null;
  /** false during SSR and hydration, so the menu does not flicker. */
  ready: boolean;
  isAdmin: boolean;
  login: (email: string, password: string) => Promise<void>;
  logout: () => void;
}

const Ctx = createContext<AuthValue | null>(null);

export function AuthProvider({ children }: { children: React.ReactNode }) {
  const token = useStore(tokenStore);
  const ready = useHydrated();

  // No expiry check here on purpose. Reading the clock during render is impure,
  // and the browser is not the authority anyway: api.ts clears the token when
  // the server answers 401, which covers expiry and revocation together.
  const session = useMemo(() => (token ? decode(token) : null), [token]);

  const login = useCallback(async (email: string, password: string) => {
    const res = await api.login({ email, password });
    tokenStore.set(res.token);
  }, []);

  const logout = useCallback(() => tokenStore.set(null), []);

  const value = useMemo<AuthValue>(
    () => ({ session, ready, isAdmin: session?.role === "ADMIN", login, logout }),
    [session, ready, login, logout],
  );

  return <Ctx.Provider value={value}>{children}</Ctx.Provider>;
}

export function useAuth() {
  const v = useContext(Ctx);
  if (!v) throw new Error("useAuth must be used inside <AuthProvider>");
  return v;
}
