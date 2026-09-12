"use client";

import Link from "next/link";
import { useParams } from "next/navigation";
import { useEffect, useState } from "react";
import { api } from "@/lib/api";
import { useCart } from "@/lib/cart";
import type { Product } from "@/lib/types";
import { Alert, Button, Card, ErrorBox, PageTitle, money } from "@/components/ui";

export default function ProductPage() {
  const { id } = useParams<{ id: string }>();
  const [product, setProduct] = useState<Product | null>(null);
  const [quantity, setQuantity] = useState(1);
  const [error, setError] = useState<unknown>(null);
  const { add } = useCart();
  const [added, setAdded] = useState(false);

  useEffect(() => {
    api.product(id).then(setProduct, setError);
  }, [id]);

  if (error) return <ErrorBox error={error} />;
  if (!product) return <Alert kind="info">Loading...</Alert>;

  return (
    <>
      <PageTitle title={product.name} />
      <Card>
        <p className="text-slate-700">{product.description}</p>
        <p className="mt-3 text-xl font-semibold">{money(product.price)}</p>
        <p className="text-sm text-slate-500">
          {product.stock > 0 ? `${product.stock} in stock` : "Out of stock"}
        </p>

        <div className="mt-4 flex items-end gap-3">
          <label className="block">
            <span className="mb-1 block text-sm font-medium text-slate-700">Quantity</span>
            <input
              type="number"
              min={1}
              max={product.stock}
              value={quantity}
              onChange={(e) => setQuantity(Number(e.target.value))}
              className="w-24 rounded border border-slate-300 px-3 py-2 text-sm"
            />
          </label>
          <Button
            disabled={product.stock === 0}
            onClick={() => {
              add(product, quantity);
              setAdded(true);
            }}
          >
            Add to cart
          </Button>
        </div>

        {added && (
          <p className="mt-3 text-sm">
            Added. <Link href="/cart" className="underline">Go to cart</Link>
          </p>
        )}
      </Card>
    </>
  );
}
