import * as React from "react";
import { Slot } from "@radix-ui/react-slot";
import { cva, type VariantProps } from "class-variance-authority";
import { cn } from "@/lib/utils";

const buttonVariants = cva(
  "inline-flex items-center justify-center gap-2 whitespace-nowrap rounded-[10px] text-sm font-medium transition-all duration-150 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[#668568] focus-visible:ring-offset-2 disabled:pointer-events-none disabled:opacity-50 active:scale-[0.98]",
  {
    variants: {
      variant: {
        default:
          "bg-[#668568] text-white shadow-sm border border-[#556F57] hover:bg-[#556F57]",
        secondary:
          "bg-white text-[#26372C] border border-[#E6E3DA] shadow-sm hover:bg-[#F1EFEA]",
        outline:
          "border border-[#E6E3DA] bg-transparent text-[#26372C] hover:bg-[#EEF3EE]",
        ghost:
          "text-[#26372C] hover:bg-[#EEF3EE]",
        destructive:
          "bg-[#FCEDE8] text-[#D76F65] border border-[#F8D8D3] hover:bg-[#F8D8D3]",
        link:
          "text-[#668568] underline-offset-4 hover:underline",
        fab:
          "h-14 w-14 rounded-full bg-[#668568] text-white shadow-lg hover:bg-[#556F57] border border-[#556F57]",
      },
      size: {
        default: "h-11 px-4 py-2",
        sm: "h-9 rounded-[8px] px-3 text-xs",
        lg: "h-12 rounded-[12px] px-8 text-base",
        icon: "h-10 w-10",
        fab: "h-14 w-14 p-0",
      },
    },
    defaultVariants: {
      variant: "default",
      size: "default",
    },
  }
);

export interface ButtonProps
  extends React.ButtonHTMLAttributes<HTMLButtonElement>,
    VariantProps<typeof buttonVariants> {
  asChild?: boolean;
}

const Button = React.forwardRef<HTMLButtonElement, ButtonProps>(
  ({ className, variant, size, asChild = false, ...props }, ref) => {
    const Comp = asChild ? Slot : "button";
    return (
      <Comp
        className={cn(buttonVariants({ variant, size, className }))}
        ref={ref}
        {...props}
      />
    );
  }
);
Button.displayName = "Button";

export { Button, buttonVariants };

