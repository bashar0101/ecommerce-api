"use client";

import { useState } from "react";
import { api } from "@/lib/api";
import { Alert, Button, Card, ErrorBox, Field, PageTitle } from "@/components/ui";

export default function ResendPage() {
  const [email, setEmail] = useState("");
  const [message, setMessage] = useState<string | null>(null);
  const [error, setError] = useState<unknown>(null);
  const [busy, setBusy] = useState(false);

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    setError(null);
    setBusy(true);
    try {
      const res = await api.resendVerification(email);
      setMessage(res.message);
    } catch (err) {
      setError(err);
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="mx-auto max-w-sm">
      <PageTitle
        title="Send the activation email again"
        subtitle="The answer is always the same, so nobody can learn which addresses have an account."
      />
      <Card>
        <form onSubmit={submit} className="space-y-4">
          <Field label="Email" type="email" value={email} required
                 onChange={(e) => setEmail(e.target.value)} />
          {message && <Alert kind="ok">{message}</Alert>}
          <ErrorBox error={error} />
          <Button type="submit" loading={busy}>Send</Button>
        </form>
        <p className="mt-3 text-xs text-slate-500">
          There is a five minute wait between emails. Asking again inside that time does nothing.
        </p>
      </Card>
    </div>
  );
}
