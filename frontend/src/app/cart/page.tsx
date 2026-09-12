"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useState } from "react";
import { api } from "@/lib/api";
import { useAuth } from "@/lib/auth";
import { useCart } from "@/lib/cart";
import { Alert, Button, Card, ErrorBox, PageTitle, money } from "@/components/ui";

export default function CartPage() {
  const { lines, total, setQuantity, remove, clear } = useCart();
  const { session, ready } = useAuth();
  const router = useRouter();
  const [error, setError] = useState<unknown>(null);
  const [busy, setBusy] = useState(false);

  async function placeOrder() {
    setError(null);
    setBusy(true);
    try {
      // One request with the whole basket: the API has no cart of its own, and it
      // checks the stock for every line inside one transaction.
      const order = await api.createOrder(
        lines.map((l) => ({ productId: l.product.id, quantity: l.quantity })),
      );
      clear();
      router.push(`/orders?placed=${order.id}`);
    } catch (err) {
      setError(err);
    } finally {
      setBusy(false);
    }
  }

  if (lines.length === 0) {
    return (
      <>
        <PageTitle title="Your cart" />
        <Alert kind="info">
          The cart is empty. <Link href="/" className="underline">Browse products</Link>
        </Alert>
      </>
    );
  }

  return (
    <>
      <PageTitle title="Your cart" subtitle="The cart lives in this browser until you order." />

      <div className="space-y-3">
        {lines.map((l) => (
          <Card key={l.product.id}>
            <div className="flex flex-wrap items-center gap-4">
              <div className="flex-1">
                <p className="font-medium">{l.product.name}</p>
                <p className="text-sm text-slate-500">{money(l.product.price)} each</p>
              </div>
              <input
                type="number"
                min={1}
                max={l.product.stock}
                value={l.quantity}
                onChange={(e) => setQuantity(l.product.id, Number(e.target.value))}
                className="w-20 rounded border border-slate-300 px-2 py-1 text-sm"
              />
              <p className="w-24 text-right font-semibold">{money(l.product.price * l.quantity)}</p>
              <Button variant="ghost" onClick={() => remove(l.product.id)}>Remove</Button>
            </div>
          </Card>
        ))}
      </div>

      <div className="mt-6 flex flex-wrap items-center justify-between gap-4">
        <p className="text-lg font-semibold">Total: {money(total)}</p>
        <div className="flex gap-3">
          <Button variant="ghost" onClick={clear}>Empty cart</Button>
          {ready && session ? (
            <Button onClick={placeOrder} loading={busy}>Place order</Button>
          ) : (
            <Link
              href="/login"
              className="rounded bg-slate-900 px-4 py-2 text-sm font-medium text-white hover:bg-slate-700"
            >
              Log in to order
            </Link>
          )}
        </div>
      </div>

      <div className="mt-4">
        <ErrorBox error={error} />
      </div>
    </>
  );
}
