import * as React from 'react';
import { cn } from '@/lib/utils';

export const Input = React.forwardRef<HTMLInputElement, React.InputHTMLAttributes<HTMLInputElement>>(({ className, ...props }, ref) => (
  <input
    ref={ref}
    className={cn('h-12 w-full rounded-2xl border-[3px] border-clay-border bg-white px-4 py-2 text-sm font-bold text-clay-ink shadow-clay-sm outline-none transition placeholder:text-clay-subtle focus:border-clay-primary focus:ring-4 focus:ring-clay-primary/25 disabled:cursor-not-allowed disabled:opacity-60', className)}
    {...props}
  />
));
Input.displayName = 'Input';
