"use client";

import Link from "next/link";
import { useSearchParams } from "next/navigation";
import { Suspense, useEffect, useState } from "react";
import { api } from "@/lib/api";
import { useAuth } from "@/lib/auth";
import type { Order } from "@/lib/types";
import { Alert, Card, ErrorBox, PageTitle, money } from "@/components/ui";

function Orders() {
  const { session, ready } = useAuth();
  const justPlaced = useSearchParams().get("placed");
  const [orders, setOrders] = useState<Order[] | null>(null);
  const [error, setError] = useState<unknown>(null);

  useEffect(() => {
    if (!ready || !session) return;
    api.myOrders().then(setOrders, setError);
  }, [ready, session]);

  if (ready && !session) {
    return (
      <Alert kind="info">
        <Link href="/login" className="underline">Log in</Link> to see your orders.
      </Alert>
    );
  }

  return (
    <>
      {justPlaced && <Alert kind="ok">Order placed. Its number is {justPlaced}.</Alert>}
      <ErrorBox error={error} />
      {!orders && !error && <Alert kind="info">Loading...</Alert>}
      {orders?.length === 0 && <Alert kind="info">You have no orders yet.</Alert>}

      <div className="mt-4 space-y-3">
        {orders?.map((o) => (
          <Card key={o.id}>
            <div className="flex flex-wrap items-baseline justify-between gap-2">
              <p className="font-medium">{new Date(o.createdAt).toLocaleString()}</p>
              <span className="rounded bg-slate-100 px-2 py-0.5 text-xs font-medium">{o.status}</span>
            </div>
            <ul className="mt-2 space-y-1 text-sm text-slate-700">
              {o.items.map((i) => (
                <li key={i.productId} className="flex justify-between">
                  <span>{i.productName} x {i.quantity}</span>
                  <span>{money(i.unitPrice * i.quantity)}</span>
                </li>
              ))}
            </ul>
            <p className="mt-2 text-right font-semibold">Total: {money(o.totalPrice)}</p>
          </Card>
        ))}
      </div>
    </>
  );
}

export default function OrdersPage() {
  return (
    <>
      <PageTitle title="My orders" />
      <Suspense fallback={<Alert kind="info">Loading...</Alert>}>
        <Orders />
      </Suspense>
    </>
  );
}
