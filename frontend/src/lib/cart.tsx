"use client";

import { createContext, useCallback, useContext, useMemo } from "react";
import { createLocalStore, useStore } from "./localStore";
import type { Product } from "./types";

/** The backend has no cart: POST /orders takes the whole basket at once. So the
 *  cart lives only in this browser until the order is placed. */
export interface CartLine {
  product: Product;
  quantity: number;
}

interface CartValue {
  lines: CartLine[];
  count: number;
  total: number;
  add: (product: Product, quantity?: number) => void;
  setQuantity: (productId: string, quantity: number) => void;
  remove: (productId: string) => void;
  clear: () => void;
}

const cartStore = createLocalStore<CartLine[]>("ecommerce.cart", []);
const Ctx = createContext<CartValue | null>(null);

export function CartProvider({ children }: { children: React.ReactNode }) {
  const lines = useStore(cartStore);

  const add = useCallback((product: Product, quantity = 1) => {
    const prev = cartStore.get();
    const found = prev.find((l) => l.product.id === product.id);
    if (!found) {
      cartStore.set([...prev, { product, quantity }]);
      return;
    }
    // Never offer more than the shop has; the server checks again anyway.
    const next = Math.min(found.quantity + quantity, product.stock);
    cartStore.set(prev.map((l) => (l.product.id === product.id ? { ...l, quantity: next } : l)));
  }, []);

  const setQuantity = useCallback((productId: string, quantity: number) => {
    cartStore.set(
      cartStore.get().flatMap((l) => {
        if (l.product.id !== productId) return [l];
        const q = Math.max(0, Math.min(quantity, l.product.stock));
        return q === 0 ? [] : [{ ...l, quantity: q }];
      }),
    );
  }, []);

  const remove = useCallback((productId: string) => {
    cartStore.set(cartStore.get().filter((l) => l.product.id !== productId));
  }, []);

  const clear = useCallback(() => cartStore.set([]), []);

  const value = useMemo<CartValue>(() => {
    const count = lines.reduce((n, l) => n + l.quantity, 0);
    const total = lines.reduce((n, l) => n + l.product.price * l.quantity, 0);
    return { lines, count, total, add, setQuantity, remove, clear };
  }, [lines, add, setQuantity, remove, clear]);

  return <Ctx.Provider value={value}>{children}</Ctx.Provider>;
}

export function useCart() {
  const v = useContext(Ctx);
  if (!v) throw new Error("useCart must be used inside <CartProvider>");
  return v;
}
