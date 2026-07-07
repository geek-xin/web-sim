import * as React from 'react';
import { cn } from '@/lib/utils';

export const Textarea = React.forwardRef<HTMLTextAreaElement, React.TextareaHTMLAttributes<HTMLTextAreaElement>>(({ className, ...props }, ref) => (
  <textarea
    ref={ref}
    className={cn('min-h-24 w-full rounded-2xl border-[3px] border-clay-border bg-[#E3F8EE] px-4 py-3 font-mono text-sm font-bold text-clay-ink shadow-clay-sm outline-none transition placeholder:text-clay-subtle focus:border-clay-primary focus:ring-4 focus:ring-clay-primary/25 disabled:cursor-not-allowed disabled:opacity-60', className)}
    {...props}
  />
));
Textarea.displayName = 'Textarea';
