"use client";

import Link from "next/link";
import { useSearchParams } from "next/navigation";
import { Suspense, useEffect, useRef, useState } from "react";
import { api } from "@/lib/api";
import { Alert, Card, ErrorBox, PageTitle } from "@/components/ui";

function Verify() {
  const token = useSearchParams().get("token");
  const [state, setState] = useState<"working" | "ok" | "fail">("working");
  const [error, setError] = useState<unknown>(null);
  // React 18+ runs effects twice in development. Without this the token would be
  // sent twice, which is harmless here (verify is idempotent) but confusing.
  const sent = useRef(false);

  useEffect(() => {
    if (!token || sent.current) return;
    sent.current = true;
    api.verify(token).then(
      () => setState("ok"),
      (err) => {
        setError(err);
        setState("fail");
      },
    );
  }, [token]);

  if (!token) return <Alert kind="error">This link has no token in it.</Alert>;

  return (
    <Card>
      {state === "working" && <Alert kind="info">Checking your link...</Alert>}
      {state === "ok" && (
        <>
          <Alert kind="ok">Your account is active. You can log in now.</Alert>
          <p className="mt-3 text-sm"><Link href="/login" className="underline">Go to login</Link></p>
        </>
      )}
      {state === "fail" && (
        <>
          <ErrorBox error={error} />
          <p className="mt-3 text-sm">
            <Link href="/resend" className="underline">Ask for a new link</Link>
          </p>
        </>
      )}
    </Card>
  );
}

export default function VerifyPage() {
  return (
    <div className="mx-auto max-w-sm">
      <PageTitle title="Activate your account" />
      {/* useSearchParams needs a Suspense boundary when the page is prerendered. */}
      <Suspense fallback={<Card><Alert kind="info">Loading...</Alert></Card>}>
        <Verify />
      </Suspense>
    </div>
  );
}
