import * as React from 'react';
import { cva, type VariantProps } from 'class-variance-authority';
import { cn } from '@/lib/utils';

const badgeVariants = cva('inline-flex items-center rounded-full border-[3px] px-3 py-1 text-xs font-black shadow-clay-sm', {
  variants: {
    variant: {
      default: 'border-clay-border bg-white text-clay-ink',
      indigo: 'border-clay-border bg-clay-cyan text-clay-ink',
      orange: 'border-clay-border bg-clay-primary text-white',
      mint: 'border-clay-border bg-clay-mint text-clay-ink',
      pink: 'border-clay-border bg-clay-pink text-clay-ink',
      yellow: 'border-clay-border bg-clay-yellow text-clay-ink',
      muted: 'border-clay-border bg-[#EFEDEA] text-clay-muted',
    },
  },
  defaultVariants: { variant: 'default' },
});

export interface BadgeProps extends React.HTMLAttributes<HTMLDivElement>, VariantProps<typeof badgeVariants> {}

export function Badge({ className, variant, ...props }: BadgeProps) {
  return <div className={cn(badgeVariants({ variant, className }))} {...props} />;
}
