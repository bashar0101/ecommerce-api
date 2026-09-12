"use client";

import Link from "next/link";
import { useSearchParams } from "next/navigation";
import { Suspense, useState } from "react";
import { api } from "@/lib/api";
import { Alert, Button, Card, ErrorBox, Field, PageTitle } from "@/components/ui";

function ResetForm() {
  // The email carries the token as text, but a link may also supply ?token=...
  const fromUrl = useSearchParams().get("token") ?? "";
  const [token, setToken] = useState(fromUrl);
  const [newPassword, setNewPassword] = useState("");
  const [done, setDone] = useState(false);
  const [error, setError] = useState<unknown>(null);
  const [busy, setBusy] = useState(false);

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    setError(null);
    setBusy(true);
    try {
      await api.resetPassword({ token, newPassword });
      setDone(true);
    } catch (err) {
      setError(err);
    } finally {
      setBusy(false);
    }
  }

  if (done) {
    return (
      <Card>
        <Alert kind="ok">Your password is changed.</Alert>
        <p className="mt-3 text-sm"><Link href="/login" className="underline">Log in</Link></p>
      </Card>
    );
  }

  return (
    <Card>
      <form onSubmit={submit} className="space-y-4">
        <Field label="Reset token" value={token} required
               onChange={(e) => setToken(e.target.value)}
               hint="Copy it from the email. It works once, and only for one hour." />
        <Field label="New password" type="password" value={newPassword} required
               onChange={(e) => setNewPassword(e.target.value)}
               hint="8-72 characters, with one uppercase, one lowercase, one digit, and no spaces." />
        <ErrorBox error={error} />
        <Button type="submit" loading={busy}>Change password</Button>
      </form>
    </Card>
  );
}

export default function ResetPasswordPage() {
  return (
    <div className="mx-auto max-w-sm">
      <PageTitle title="Set a new password" />
      <Suspense fallback={<Card><Alert kind="info">Loading...</Alert></Card>}>
        <ResetForm />
      </Suspense>
    </div>
  );
}
