import { CircleAlert, X } from "lucide-react";
import { Button } from "@/components/ui/button";
import { cn } from "@/lib/utils";

export type FailureAlertProps = {
  /** What did not happen, e.g. "The room could not be saved". */
  title: string;
  /** Why, in words for the Administrator; usually `explain(error)` from `@/api/problem`. */
  reason: string;
  /** Offers a button that hides the alert, when given. */
  onDismiss?: () => void;
  className?: string;
};

/** Tells the Administrator that an action failed and why, announced to assistive technology as it appears. */
export function FailureAlert({ title, reason, onDismiss, className }: FailureAlertProps) {
  return (
    <div
      role="alert"
      className={cn(
        "flex items-start gap-2 rounded-md border border-destructive/50 px-3 py-2.5 text-sm text-destructive",
        className,
      )}
    >
      <CircleAlert aria-hidden className="mt-0.5 size-4 shrink-0" />
      <div className="min-w-0 flex-1 space-y-0.5 [overflow-wrap:anywhere]">
        <p className="font-medium">{title}</p>
        <p>{reason}</p>
      </div>
      {onDismiss && (
        <Button
          variant="ghost"
          size="icon"
          className="-my-1 -mr-1.5 size-7 shrink-0 text-destructive hover:bg-destructive/10 hover:text-destructive"
          aria-label="Dismiss"
          onClick={onDismiss}
        >
          <X aria-hidden />
        </Button>
      )}
    </div>
  );
}
