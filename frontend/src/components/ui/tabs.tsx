import * as React from 'react';
import * as TabsPrimitive from '@radix-ui/react-tabs';
import { cn } from '@/lib/utils';

export const Tabs = TabsPrimitive.Root;
export const TabsList = React.forwardRef<React.ElementRef<typeof TabsPrimitive.List>, React.ComponentPropsWithoutRef<typeof TabsPrimitive.List>>(({ className, ...props }, ref) => <TabsPrimitive.List ref={ref} className={cn('inline-flex flex-wrap items-center gap-2 rounded-3xl border-[3px] border-clay-border bg-white p-2 shadow-clay-sm', className)} {...props} />);
TabsList.displayName = TabsPrimitive.List.displayName;
export const TabsTrigger = React.forwardRef<React.ElementRef<typeof TabsPrimitive.Trigger>, React.ComponentPropsWithoutRef<typeof TabsPrimitive.Trigger>>(({ className, ...props }, ref) => <TabsPrimitive.Trigger ref={ref} className={cn('cursor-pointer rounded-2xl border-[3px] border-transparent px-4 py-2 text-sm font-black text-clay-muted transition hover:border-clay-border hover:bg-clay-cream hover:text-clay-ink data-[state=active]:border-clay-border data-[state=active]:bg-clay-primary data-[state=active]:text-white data-[state=active]:shadow-clay-sm disabled:cursor-not-allowed disabled:opacity-60 disabled:hover:border-transparent disabled:hover:bg-transparent disabled:hover:text-clay-muted focus-visible:outline-none focus-visible:ring-4 focus-visible:ring-clay-primary/35', className)} {...props} />);
TabsTrigger.displayName = TabsPrimitive.Trigger.displayName;
export const TabsContent = React.forwardRef<React.ElementRef<typeof TabsPrimitive.Content>, React.ComponentPropsWithoutRef<typeof TabsPrimitive.Content>>(({ className, ...props }, ref) => <TabsPrimitive.Content ref={ref} className={cn('mt-4 outline-none focus-visible:ring-4 focus-visible:ring-clay-primary/40', className)} {...props} />);
TabsContent.displayName = TabsPrimitive.Content.displayName;
