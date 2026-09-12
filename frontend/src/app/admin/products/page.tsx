"use client";

import Link from "next/link";
import { useCallback, useEffect, useState } from "react";
import { api } from "@/lib/api";
import { useAuth } from "@/lib/auth";
import type { Product, ProductInput } from "@/lib/types";
import { Alert, Button, Card, ErrorBox, Field, PageTitle, money } from "@/components/ui";

const EMPTY: ProductInput = { name: "", price: 0, stock: 0, description: "" };

export default function AdminProductsPage() {
  const { session, ready, isAdmin } = useAuth();
  const [products, setProducts] = useState<Product[]>([]);
  const [form, setForm] = useState<ProductInput>(EMPTY);
  const [editingId, setEditingId] = useState<string | null>(null);
  const [error, setError] = useState<unknown>(null);
  const [message, setMessage] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  // size=100 because there is no admin-specific listing endpoint yet.
  const load = useCallback(async () => {
    try {
      const data = await api.products(0, 100);
      setProducts(data.content);
    } catch (err) {
      setError(err);
    }
  }, []);

  useEffect(() => {
    let cancelled = false;
    (async () => {
      try {
        const data = await api.products(0, 100);
        if (!cancelled) setProducts(data.content);
      } catch (err) {
        if (!cancelled) setError(err);
      }
    })();
    return () => {
      cancelled = true;
    };
  }, []);

  const set = (k: keyof ProductInput) => (e: React.ChangeEvent<HTMLInputElement>) =>
    setForm({ ...form, [k]: k === "price" || k === "stock" ? Number(e.target.value) : e.target.value });

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    setError(null);
    setMessage(null);
    setBusy(true);
    try {
      if (editingId) {
        await api.updateProduct(editingId, form);
        setMessage("Product updated.");
      } else {
        await api.createProduct(form);
        setMessage("Product created.");
      }
      setForm(EMPTY);
      setEditingId(null);
      await load();
    } catch (err) {
      setError(err);
    } finally {
      setBusy(false);
    }
  }

  async function del(id: string) {
    setError(null);
    setMessage(null);
    try {
      await api.deleteProduct(id);
      setMessage("Product deleted.");
      await load();
    } catch (err) {
      setError(err);
    }
  }

  // The server enforces this; hiding the form is only to avoid pointless 403s.
  if (ready && !session) {
    return <Alert kind="info"><Link href="/login" className="underline">Log in</Link> as an admin.</Alert>;
  }
  if (ready && !isAdmin) {
    return (
      <Alert kind="error">
        This page needs an ADMIN account. Promote yourself in the database:
        <code className="mt-2 block rounded bg-red-100 px-2 py-1 text-xs">
          UPDATE users SET role = &apos;ADMIN&apos; WHERE email = &apos;{session?.email}&apos;;
        </code>
        Then log out and log in again - the role is copied into the token at login.
      </Alert>
    );
  }

  return (
    <>
      <PageTitle title="Manage products" subtitle="Create, change and delete. ADMIN only." />

      <Card>
        <form onSubmit={submit} className="space-y-4">
          <h2 className="font-medium">{editingId ? "Edit product" : "New product"}</h2>
          <Field label="Name" value={form.name} required onChange={set("name")} />
          <Field label="Description" value={form.description} required onChange={set("description")} />
          <div className="grid gap-4 sm:grid-cols-2">
            <Field label="Price" type="number" step="0.01" min="0.01" value={form.price}
                   required onChange={set("price")} hint="Must be more than zero." />
            <Field label="Stock" type="number" min="0" value={form.stock}
                   required onChange={set("stock")} />
          </div>
          {message && <Alert kind="ok">{message}</Alert>}
          <ErrorBox error={error} />
          <div className="flex gap-3">
            <Button type="submit" loading={busy}>{editingId ? "Save changes" : "Create"}</Button>
            {editingId && (
              <Button type="button" variant="ghost"
                      onClick={() => { setEditingId(null); setForm(EMPTY); }}>
                Cancel
              </Button>
            )}
          </div>
        </form>
      </Card>

      <h2 className="mt-8 mb-3 font-medium">All products ({products.length})</h2>
      <div className="space-y-3">
        {products.map((p) => (
          <Card key={p.id}>
            <div className="flex flex-wrap items-center gap-4">
              <div className="flex-1">
                <p className="font-medium">{p.name}</p>
                <p className="text-sm text-slate-500">
                  {money(p.price)} - {p.stock} in stock
                </p>
              </div>
              <Button
                variant="ghost"
                onClick={() => {
                  setEditingId(p.id);
                  setForm({ name: p.name, price: p.price, stock: p.stock, description: p.description });
                  window.scrollTo({ top: 0, behavior: "smooth" });
                }}
              >
                Edit
              </Button>
              <Button variant="danger" onClick={() => del(p.id)}>Delete</Button>
            </div>
          </Card>
        ))}
      </div>
    </>
  );
}
