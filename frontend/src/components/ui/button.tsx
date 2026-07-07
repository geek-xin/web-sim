import * as React from 'react';
import { Slot } from '@radix-ui/react-slot';
import { cva, type VariantProps } from 'class-variance-authority';
import { cn } from '@/lib/utils';

const buttonVariants = cva(
  'inline-flex cursor-pointer items-center justify-center gap-2 whitespace-nowrap rounded-2xl border-[3px] border-clay-border text-sm font-black shadow-clay-sm transition-all duration-200 hover:-translate-y-0.5 hover:shadow-clay active:translate-y-0 active:shadow-clay-sm focus-visible:outline-none focus-visible:ring-4 focus-visible:ring-clay-primary/35 disabled:pointer-events-none disabled:opacity-50',
  {
    variants: {
      variant: {
        default: 'bg-white text-clay-ink hover:bg-clay-cream',
        primary: 'bg-clay-primary text-white hover:bg-clay-cta-dark',
        orange: 'bg-clay-gold text-clay-ink hover:bg-[#FFF7A6]',
        danger: 'bg-clay-error text-white hover:bg-[#FF6D5F]',
        ghost: 'border-transparent bg-transparent shadow-none text-clay-ink hover:border-clay-border hover:bg-white hover:shadow-clay-sm',
        outline: 'bg-white text-clay-ink hover:bg-clay-cream',
      },
      size: {
        default: 'h-12 px-5 py-2',
        sm: 'h-10 rounded-xl px-3 text-xs',
        lg: 'h-14 rounded-3xl px-7 text-base',
        icon: 'h-11 w-11 rounded-2xl p-0',
      },
    },
    defaultVariants: {
      variant: 'default',
      size: 'default',
    },
  },
);

export interface ButtonProps extends React.ButtonHTMLAttributes<HTMLButtonElement>, VariantProps<typeof buttonVariants> {
  asChild?: boolean;
}

const Button = React.forwardRef<HTMLButtonElement, ButtonProps>(({ className, variant, size, asChild = false, ...props }, ref) => {
  const Comp = asChild ? Slot : 'button';
  return <Comp className={cn(buttonVariants({ variant, size, className }))} ref={ref} {...props} />;
});
Button.displayName = 'Button';

export { Button, buttonVariants };
