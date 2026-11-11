import { HealthStatusEnum } from "@/api/generated";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { cn } from "@/lib/utils";
import { useHealth } from "./useHealth";

type Indicator = { label: string; tone: "ok" | "down" | "pending" };

function useIndicator(): Indicator {
  const health = useHealth();
  if (health.isPending) return { label: "Checking…", tone: "pending" };
  if (health.isError) return { label: "Unreachable", tone: "down" };
  return health.data.status === HealthStatusEnum.Up ? { label: "Up", tone: "ok" } : { label: "Down", tone: "down" };
}

export function HealthPage() {
  const indicator = useIndicator();

  return (
    <main className="flex min-h-screen items-center justify-center bg-muted/40 p-6">
      <Card className="w-full max-w-sm">
        <CardHeader>
          <CardTitle>
            <h1 className="text-xl">Room Booking</h1>
          </CardTitle>
          <CardDescription>Status of the service and its database.</CardDescription>
        </CardHeader>
        <CardContent>
          <div className="flex items-center justify-between rounded-md border px-4 py-3">
            <span className="text-sm font-medium">Service</span>
            <span
              role="status"
              aria-label="Service status"
              className={cn(
                "inline-flex items-center gap-2 text-sm",
                indicator.tone === "ok" && "text-emerald-700",
                indicator.tone === "down" && "text-destructive",
                indicator.tone === "pending" && "text-muted-foreground",
              )}
            >
              <span
                aria-hidden
                className={cn(
                  "size-2 rounded-full",
                  indicator.tone === "ok" && "bg-emerald-600",
                  indicator.tone === "down" && "bg-destructive",
                  indicator.tone === "pending" && "animate-pulse bg-muted-foreground",
                )}
              />
              {indicator.label}
            </span>
          </div>
        </CardContent>
      </Card>
    </main>
  );
}
