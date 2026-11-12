import { LoaderCircle } from "lucide-react";
import { type ComponentProps, type FormEvent, forwardRef, useId, useRef, useState } from "react";
import type { RoomInput } from "@/api/generated";
import { ApiError, explain } from "@/api/problem";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogClose,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { cn } from "@/lib/utils";
import { FailureAlert } from "./FailureAlert";

const NAME_MAX_LENGTH = 100;
const ROOM_NAME_TAKEN = "urn:room-booking:problem:room-name-taken";

type Field = keyof RoomInput;

/** What the Administrator typed, kept as text so the form shows exactly that. */
type RoomValues = { name: string; capacity: string };

type RoomErrors = Partial<Record<Field | "general", string>>;

export type RoomDialogProps = {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  title: string;
  description: string;
  submitLabel: string;
  /** The values the form starts with each time it opens; empty when omitted. */
  initialValues?: RoomInput;
  /** Sends the Room to the server. The dialog closes when it resolves and shows why when it rejects. */
  onSave: (input: RoomInput) => Promise<unknown>;
};

/**
 * The single dialog that describes a Room, to create or to edit one. It flags invalid input before sending it, and
 * when the server rejects it, it stays open with the input and shows each error next to the field it concerns.
 */
export function RoomDialog({ open, onOpenChange, ...formProps }: RoomDialogProps) {
  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-w-md">
        {/* Mounted only while open, so the form starts afresh every time. */}
        <RoomForm {...formProps} onSaved={() => onOpenChange(false)} />
      </DialogContent>
    </Dialog>
  );
}

type RoomFormProps = Omit<RoomDialogProps, "open" | "onOpenChange"> & { onSaved: () => void };

function RoomForm({ title, description, submitLabel, initialValues, onSave, onSaved }: RoomFormProps) {
  const [values, setValues] = useState<RoomValues>({
    name: initialValues?.name ?? "",
    capacity: initialValues ? String(initialValues.capacity) : "",
  });
  const [errors, setErrors] = useState<RoomErrors>({});
  const [saving, setSaving] = useState(false);
  const inputs = { name: useRef<HTMLInputElement>(null), capacity: useRef<HTMLInputElement>(null) };

  function change(field: Field, value: string) {
    setValues((current) => ({ ...current, [field]: value }));
    setErrors(({ [field]: _fixed, ...rest }) => rest);
  }

  function show(found: RoomErrors) {
    setErrors(found);
    const firstInvalid = (["name", "capacity"] as const).find((field) => found[field]);
    if (firstInvalid) inputs[firstInvalid].current?.focus();
  }

  async function submit(event: FormEvent) {
    event.preventDefault();
    const invalid = validate(values);
    if (Object.keys(invalid).length > 0) return show(invalid);

    setSaving(true);
    setErrors({});
    try {
      await onSave({ name: values.name, capacity: Number(values.capacity) });
      onSaved();
    } catch (error) {
      setSaving(false);
      show(errorsFrom(error));
    }
  }

  return (
    <form noValidate onSubmit={submit} className="grid gap-6">
      <DialogHeader>
        <DialogTitle>{title}</DialogTitle>
        <DialogDescription>{description}</DialogDescription>
      </DialogHeader>
      {errors.general && <FailureAlert title="The room could not be saved" reason={errors.general} />}
      <div className="grid gap-4">
        <TextField
          ref={inputs.name}
          label="Name"
          value={values.name}
          error={errors.name}
          onChange={(value) => change("name", value)}
          autoComplete="off"
        />
        <TextField
          ref={inputs.capacity}
          label="Capacity"
          hint="How many people the room holds."
          value={values.capacity}
          error={errors.capacity}
          onChange={(value) => change("capacity", value)}
          type="number"
          inputMode="numeric"
          min={1}
          step={1}
          className="w-32"
        />
      </div>
      <DialogFooter className="gap-2 sm:gap-0">
        <DialogClose asChild>
          <Button type="button" variant="outline">
            Cancel
          </Button>
        </DialogClose>
        <Button type="submit" disabled={saving}>
          {saving && <LoaderCircle aria-hidden className="animate-spin" />}
          {submitLabel}
        </Button>
      </DialogFooter>
    </form>
  );
}

type TextFieldProps = Omit<ComponentProps<typeof Input>, "onChange" | "value" | "ref"> & {
  label: string;
  hint?: string;
  value: string;
  error: string | undefined;
  onChange: (value: string) => void;
};

const TextField = forwardRef<HTMLInputElement, TextFieldProps>(function TextField(
  { label, hint, value, error, onChange, className, ...inputProps },
  ref,
) {
  const id = useId();
  const hintId = `${id}-hint`;
  const errorId = `${id}-error`;
  const describedBy = [error && errorId, hint && hintId].filter(Boolean).join(" ") || undefined;

  return (
    <div className="grid gap-2">
      <Label htmlFor={id}>{label}</Label>
      <Input
        ref={ref}
        id={id}
        value={value}
        onChange={(event) => onChange(event.target.value)}
        aria-invalid={error ? true : undefined}
        aria-describedby={describedBy}
        className={cn(error && "border-destructive focus-visible:ring-destructive", className)}
        {...inputProps}
      />
      {error && (
        <p id={errorId} className="text-sm text-destructive">
          {error}
        </p>
      )}
      {hint && !error && (
        <p id={hintId} className="text-sm text-muted-foreground">
          {hint}
        </p>
      )}
    </div>
  );
});

/** Mirrors the server's rules (see RoomInput in the Contract), as early feedback. */
function validate({ name, capacity }: RoomValues): RoomErrors {
  const errors: RoomErrors = {};
  // `trim()` strips the same whitespace the server trims, no-break spaces included (see Room in the backend).
  if (name.trim() === "") errors.name = "Enter a name.";
  else if (name.length > NAME_MAX_LENGTH) errors.name = `Use at most ${NAME_MAX_LENGTH} characters.`;
  if (capacity.trim() === "") errors.capacity = "Enter a capacity.";
  else if (!/^\d+$/.test(capacity.trim()) || Number(capacity) < 1)
    errors.capacity = "Enter a whole number of at least 1.";
  return errors;
}

/** Places each error the server answered next to its field, and anything else in the general message. */
function errorsFrom(error: unknown): RoomErrors {
  if (!(error instanceof Error)) return { general: "Something went wrong." };
  const problem = error instanceof ApiError ? error.problem : undefined;
  if (problem?.type === ROOM_NAME_TAKEN) return { name: problem.detail ?? problem.title };

  const errors: RoomErrors = {};
  for (const { field, message } of problem?.errors ?? []) {
    if (isField(field)) errors[field] = message;
  }
  if (Object.keys(errors).length === 0) errors.general = explain(error);
  return errors;
}

function isField(field: string): field is Field {
  return field === "name" || field === "capacity";
}
