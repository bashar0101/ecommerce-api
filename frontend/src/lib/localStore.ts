import { useSyncExternalStore } from "react";

/**
 * A tiny store over localStorage.
 *
 * Reading localStorage inside useEffect and calling setState is the obvious
 * approach, but it renders once with the wrong value and then again with the
 * right one - which is what React's set-state-in-effect rule warns about.
 * useSyncExternalStore is built for exactly this: an external source of truth
 * that the server cannot see. getServerSnapshot supplies the empty value during
 * SSR and hydration, then React switches to the real one.
 */
export function createLocalStore<T>(key: string, fallback: T) {
  let cache: T = fallback;
  let loaded = false;
  const listeners = new Set<() => void>();

  function emit() {
    listeners.forEach((l) => l());
  }

  return {
    subscribe(listener: () => void) {
      listeners.add(listener);

      // Also react to writes made outside React: api.ts clears the token on a
      // 401, and another tab may log in or out. Dropping the cache forces the
      // next getSnapshot to re-read storage.
      const onStorage = (e: StorageEvent) => {
        if (e.key === null || e.key === key) {
          loaded = false;
          cache = fallback;
          listener();
        }
      };
      window.addEventListener("storage", onStorage);

      return () => {
        listeners.delete(listener);
        window.removeEventListener("storage", onStorage);
      };
    },

    /** Must return a stable reference, so the value is cached, not re-parsed. */
    getSnapshot(): T {
      if (!loaded) {
        loaded = true;
        try {
          const raw = window.localStorage.getItem(key);
          if (raw) cache = JSON.parse(raw) as T;
        } catch {
          /* private mode, blocked or corrupt storage: keep the fallback */
        }
      }
      return cache;
    },

    getServerSnapshot(): T {
      return fallback;
    },

    set(next: T) {
      cache = next;
      loaded = true;
      try {
        if (next === null) window.localStorage.removeItem(key);
        else window.localStorage.setItem(key, JSON.stringify(next));
      } catch {
        /* ignore: the value still works for this page view */
      }
      emit();
    },

    get(): T {
      return this.getSnapshot();
    },
  };
}

export function useStore<T>(store: ReturnType<typeof createLocalStore<T>>): T {
  return useSyncExternalStore(store.subscribe, store.getSnapshot, store.getServerSnapshot);
}

const noopSubscribe = () => () => {};

/**
 * false while rendering on the server and during hydration, true afterwards.
 * Lets the menu avoid showing "Login" for a split second to someone who is
 * already logged in, without any effect or state.
 */
export function useHydrated(): boolean {
  return useSyncExternalStore(
    noopSubscribe,
    () => true,
    () => false,
  );
}
