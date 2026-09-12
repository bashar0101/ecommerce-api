"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useState } from "react";
import { ApiError } from "@/lib/api";
import { useAuth } from "@/lib/auth";
import { Button, Card, ErrorBox, Field, PageTitle } from "@/components/ui";

export default function LoginPage() {
  const { login } = useAuth();
  const router = useRouter();
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState<unknown>(null);
  const [busy, setBusy] = useState(false);

  const unverified = error instanceof ApiError && error.message === "Account not verified";

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    setError(null);
    setBusy(true);
    try {
      await login(email, password);
      router.push("/");
    } catch (err) {
      setError(err);
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="mx-auto max-w-sm">
      <PageTitle title="Log in" />
      <Card>
        <form onSubmit={submit} className="space-y-4">
          <Field label="Email" type="email" value={email} required
                 onChange={(e) => setEmail(e.target.value)} />
          <Field label="Password" type="password" value={password} required
                 onChange={(e) => setPassword(e.target.value)} />

          <ErrorBox error={error} />
          {/* The API distinguishes "not verified" from "wrong password", so the
              UI can offer the action that actually helps. */}
          {unverified && (
            <p className="text-sm">
              <Link href="/resend" className="underline">Send the activation email again</Link>
            </p>
          )}

          <Button type="submit" loading={busy}>Log in</Button>
        </form>

        <div className="mt-4 space-y-1 text-sm text-slate-600">
          <p><Link href="/forgot-password" className="underline">Forgot your password?</Link></p>
          <p>No account? <Link href="/register" className="underline">Register</Link></p>
        </div>
      </Card>
    </div>
  );
}
