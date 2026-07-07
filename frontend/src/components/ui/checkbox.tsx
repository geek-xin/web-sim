import * as React from 'react';
import * as CheckboxPrimitive from '@radix-ui/react-checkbox';
import { Check } from 'lucide-react';
import { cn } from '@/lib/utils';

export const Checkbox = React.forwardRef<React.ElementRef<typeof CheckboxPrimitive.Root>, React.ComponentPropsWithoutRef<typeof CheckboxPrimitive.Root>>(({ className, ...props }, ref) => (
  <CheckboxPrimitive.Root ref={ref} className={cn('peer h-6 w-6 shrink-0 cursor-pointer rounded-md border-[3px] border-clay-border bg-white shadow-clay-sm focus-visible:outline-none focus-visible:ring-4 focus-visible:ring-clay-primary/35 disabled:cursor-not-allowed disabled:opacity-50 data-[state=checked]:bg-clay-primary', className)} {...props}>
    <CheckboxPrimitive.Indicator className="flex items-center justify-center text-white">
      <Check className="h-4 w-4" />
    </CheckboxPrimitive.Indicator>
  </CheckboxPrimitive.Root>
));
Checkbox.displayName = CheckboxPrimitive.Root.displayName;
