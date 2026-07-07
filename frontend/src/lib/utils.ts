import { type ClassValue, clsx } from 'clsx';
import { twMerge } from 'tailwind-merge';

export function cn(...inputs: ClassValue[]) {
  return twMerge(clsx(inputs));
}

export function normalizedOptionalText(value?: string | null): string | null {
  const text = (value || '').trim();
  return text || null;
}

export function formatDuration(ms?: number | null): string {
  const value = Number(ms || 0);
  if (value >= 1000) {
    return (value / 1000).toFixed(value >= 10000 ? 0 : 1) + 's';
  }
  return value + 'ms';
}
