"use client";

import Link from "next/link";
import { useState } from "react";
import { api } from "@/lib/api";
import { Alert, Button, Card, ErrorBox, Field, PageTitle } from "@/components/ui";

export default function ForgotPasswordPage() {
  const [email, setEmail] = useState("");
  const [message, setMessage] = useState<string | null>(null);
  const [error, setError] = useState<unknown>(null);
  const [busy, setBusy] = useState(false);

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    setError(null);
    setBusy(true);
    try {
      const res = await api.forgotPassword(email);
      setMessage(res.message);
    } catch (err) {
      setError(err);
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="mx-auto max-w-sm">
      <PageTitle title="Forgot your password" />
      <Card>
        <form onSubmit={submit} className="space-y-4">
          <Field label="Email" type="email" value={email} required
                 onChange={(e) => setEmail(e.target.value)} />
          {message && <Alert kind="ok">{message}</Alert>}
          <ErrorBox error={error} />
          <Button type="submit" loading={busy}>Send reset email</Button>
        </form>
        <p className="mt-4 text-sm text-slate-600">
          Have the token already?{" "}
          <Link href="/reset-password" className="underline">Set a new password</Link>
        </p>
      </Card>
    </div>
  );
}
