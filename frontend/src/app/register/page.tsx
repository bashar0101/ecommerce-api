"use client";

import Link from "next/link";
import { useState } from "react";
import { api } from "@/lib/api";
import { Alert, Button, Card, ErrorBox, Field, PageTitle } from "@/components/ui";

export default function RegisterPage() {
  const [form, setForm] = useState({ firstName: "", lastName: "", email: "", password: "" });
  const [done, setDone] = useState(false);
  const [error, setError] = useState<unknown>(null);
  const [busy, setBusy] = useState(false);

  const set = (k: keyof typeof form) => (e: React.ChangeEvent<HTMLInputElement>) =>
    setForm({ ...form, [k]: e.target.value });

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    setError(null);
    setBusy(true);
    try {
      await api.register(form);
      setDone(true);
    } catch (err) {
      setError(err);
    } finally {
      setBusy(false);
    }
  }

  if (done) {
    return (
      <div className="mx-auto max-w-sm">
        <PageTitle title="Check your email" />
        <Card>
          <Alert kind="ok">
            We sent an activation link to <strong>{form.email}</strong>. You cannot log in until
            you open it.
          </Alert>
          <p className="mt-3 text-sm text-slate-600">
            Nothing arrived? <Link href="/resend" className="underline">Send it again</Link>.
          </p>
        </Card>
      </div>
    );
  }

  return (
    <div className="mx-auto max-w-sm">
      <PageTitle title="Create an account" />
      <Card>
        <form onSubmit={submit} className="space-y-4">
          <Field label="First name" value={form.firstName} required onChange={set("firstName")} />
          <Field label="Last name" value={form.lastName} required onChange={set("lastName")} />
          <Field label="Email" type="email" value={form.email} required onChange={set("email")} />
          {/* Mirrors the server rules so the user sees them before submitting.
              The server checks again - this is convenience, not security. */}
          <Field
            label="Password"
            type="password"
            value={form.password}
            required
            onChange={set("password")}
            hint="8-72 characters, with one uppercase, one lowercase, one digit, and no spaces."
          />
          <ErrorBox error={error} />
          <Button type="submit" loading={busy}>Register</Button>
        </form>
        <p className="mt-4 text-sm text-slate-600">
          Already have an account? <Link href="/login" className="underline">Log in</Link>
        </p>
      </Card>
    </div>
  );
}
