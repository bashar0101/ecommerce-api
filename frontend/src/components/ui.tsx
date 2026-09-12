"use client";

import Link from "next/link";
import { ApiError } from "@/lib/api";

export function Alert({ kind, children }: { kind: "error" | "ok" | "info"; children: React.ReactNode }) {
  const tone =
    kind === "error"
      ? "border-red-300 bg-red-50 text-red-900"
      : kind === "ok"
        ? "border-green-300 bg-green-50 text-green-900"
        : "border-slate-300 bg-slate-50 text-slate-800";
  return <div className={`rounded border px-3 py-2 text-sm ${tone}`}>{children}</div>;
}

/** Shows the server's message, then any per-field messages from `fields`. */
export function ErrorBox({ error }: { error: unknown }) {
  if (!error) return null;
  const api = error instanceof ApiError ? error : null;
  const message = api?.message ?? (error instanceof Error ? error.message : String(error));
  return (
    <Alert kind="error">
      <p className="font-medium">{message}</p>
      {api?.fields && (
        <ul className="mt-1 list-inside list-disc">
          {Object.entries(api.fields).map(([field, text]) => (
            <li key={field}>
              <span className="font-medium">{field}</span>: {text}
            </li>
          ))}
        </ul>
      )}
    </Alert>
  );
}

export function Field({
  label, hint, ...props
}: React.InputHTMLAttributes<HTMLInputElement> & { label: string; hint?: string }) {
  return (
    <label className="block">
      <span className="mb-1 block text-sm font-medium text-slate-700">{label}</span>
      <input
        {...props}
        className="w-full rounded border border-slate-300 px-3 py-2 text-sm outline-none focus:border-slate-900"
      />
      {hint && <span className="mt-1 block text-xs text-slate-500">{hint}</span>}
    </label>
  );
}

export function Button({
  loading, variant = "primary", ...props
}: React.ButtonHTMLAttributes<HTMLButtonElement> & {
  loading?: boolean;
  variant?: "primary" | "ghost" | "danger";
}) {
  const tone =
    variant === "primary"
      ? "bg-slate-900 text-white hover:bg-slate-700 disabled:bg-slate-400"
      : variant === "danger"
        ? "bg-red-600 text-white hover:bg-red-500 disabled:bg-red-300"
        : "border border-slate-300 text-slate-800 hover:bg-slate-100";
  return (
    <button
      {...props}
      disabled={props.disabled || loading}
      className={`rounded px-4 py-2 text-sm font-medium transition disabled:cursor-not-allowed ${tone}`}
    >
      {loading ? "Working..." : props.children}
    </button>
  );
}

export function Card({ children }: { children: React.ReactNode }) {
  return <div className="rounded-lg border border-slate-200 bg-white p-4 shadow-sm">{children}</div>;
}

export function PageTitle({ title, subtitle }: { title: string; subtitle?: string }) {
  return (
    <header className="mb-6">
      <h1 className="text-2xl font-semibold text-slate-900">{title}</h1>
      {subtitle && <p className="mt-1 text-sm text-slate-600">{subtitle}</p>}
    </header>
  );
}

export const money = (n: number) =>
  new Intl.NumberFormat("en-US", { style: "currency", currency: "USD" }).format(n);

export function NavLink({ href, children }: { href: string; children: React.ReactNode }) {
  return (
    <Link href={href} className="text-sm text-slate-700 hover:text-slate-950 hover:underline">
      {children}
    </Link>
  );
}
