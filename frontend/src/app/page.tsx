"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { api } from "@/lib/api";
import { useCart } from "@/lib/cart";
import type { Page, Product } from "@/lib/types";
import { Alert, Button, Card, ErrorBox, PageTitle, money } from "@/components/ui";

export default function ProductsPage() {
  const [data, setData] = useState<Page<Product> | null>(null);
  const [page, setPage] = useState(0);
  const [error, setError] = useState<unknown>(null);
  const [loading, setLoading] = useState(true);
  const { add } = useCart();

  // The first state change happens after the await, never synchronously inside
  // the effect - that is what React's set-state-in-effect rule asks for. The
  // "cancelled" flag stops a slow page-1 response overwriting page 2.
  useEffect(() => {
    let cancelled = false;
    (async () => {
      try {
        const data = await api.products(page);
        if (!cancelled) setData(data);
      } catch (err) {
        if (!cancelled) setError(err);
      } finally {
        if (!cancelled) setLoading(false);
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [page]);

  // Paging is a user action, so setting the spinner here is allowed.
  const goToPage = (n: number) => {
    setLoading(true);
    setError(null);
    setPage(n);
  };

  return (
    <>
      <PageTitle title="Products" subtitle="Anyone can browse. You need an account only to order." />

      {loading && <Alert kind="info">Loading... the free server may need up to a minute to wake up.</Alert>}
      <ErrorBox error={error} />

      {data && data.content.length === 0 && <Alert kind="info">No products yet.</Alert>}

      <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
        {data?.content.map((p) => (
          <Card key={p.id}>
            <div className="flex h-full flex-col">
              <Link href={`/products/${p.id}`} className="font-medium hover:underline">
                {p.name}
              </Link>
              <p className="mt-1 flex-1 text-sm text-slate-600">{p.description}</p>
              <p className="mt-2 font-semibold">{money(p.price)}</p>
              <p className="text-xs text-slate-500">
                {p.stock > 0 ? `${p.stock} in stock` : "Out of stock"}
              </p>
              <div className="mt-3">
                <Button onClick={() => add(p)} disabled={p.stock === 0}>
                  Add to cart
                </Button>
              </div>
            </div>
          </Card>
        ))}
      </div>

      {data && data.page.totalPages > 1 && (
        <div className="mt-6 flex items-center gap-3">
          <Button variant="ghost" disabled={page === 0} onClick={() => goToPage(page - 1)}>
            Previous
          </Button>
          <span className="text-sm text-slate-600">
            Page {data.page.number + 1} of {data.page.totalPages}
          </span>
          <Button
            variant="ghost"
            disabled={page >= data.page.totalPages - 1}
            onClick={() => goToPage(page + 1)}
          >
            Next
          </Button>
        </div>
      )}
    </>
  );
}
