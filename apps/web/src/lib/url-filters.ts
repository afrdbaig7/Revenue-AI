"use client";
import { useCallback, useEffect, useRef, useState } from "react";

export function useUrlFilters<T extends Record<string, string>>(defaults: T): [T, (changes: Partial<T>) => void] {
  const initial = useRef(defaults);
  const [filters, setFilters] = useState(defaults);
  useEffect(() => {
    const read = () => {
      const query = new URLSearchParams(window.location.search);
      const next = { ...initial.current };
      for (const key of Object.keys(next) as (keyof T)[]) next[key] = (query.get(String(key)) ?? initial.current[key]) as T[keyof T];
      setFilters(next);
    };
    read();
    window.addEventListener("popstate", read);
    return () => window.removeEventListener("popstate", read);
  }, []);
  const update = useCallback((changes: Partial<T>) => {
    setFilters((previous) => {
      const next = { ...previous, ...changes };
      const url = new URL(window.location.href);
      for (const key of Object.keys(next)) {
        if (next[key] === initial.current[key] || next[key] === "") url.searchParams.delete(key);
        else url.searchParams.set(key, next[key]);
      }
      window.history.replaceState(null, "", url);
      return next;
    });
  }, []);
  return [filters, update];
}
