"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useAuth } from "@/lib/auth";
import { useCart } from "@/lib/cart";
import { NavLink } from "./ui";

export function Nav() {
  const { session, ready, isAdmin, logout } = useAuth();
  const { count } = useCart();
  const router = useRouter();

  return (
    <nav className="border-b border-slate-200 bg-white">
      <div className="mx-auto flex max-w-5xl flex-wrap items-center gap-4 px-4 py-3">
        <Link href="/" className="font-semibold text-slate-900">
          Shop
        </Link>

        <div className="flex flex-1 flex-wrap items-center gap-4">
          <NavLink href="/">Products</NavLink>
          <NavLink href="/cart">Cart{count > 0 && ` (${count})`}</NavLink>
          {session && <NavLink href="/orders">My orders</NavLink>}
          {isAdmin && <NavLink href="/admin/products">Admin</NavLink>}
        </div>

        {/* `ready` stops the menu flashing "Login" before localStorage is read. */}
        {ready && (
          <div className="flex items-center gap-3">
            {session ? (
              <>
                <span className="text-xs text-slate-500">
                  {session.email} {isAdmin && <strong>(admin)</strong>}
                </span>
                <button
                  onClick={() => {
                    logout();
                    router.push("/");
                  }}
                  className="text-sm text-slate-700 underline hover:text-slate-950"
                >
                  Log out
                </button>
              </>
            ) : (
              <>
                <NavLink href="/login">Login</NavLink>
                <NavLink href="/register">Register</NavLink>
              </>
            )}
          </div>
        )}
      </div>
    </nav>
  );
}
