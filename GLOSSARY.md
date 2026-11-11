# Room Booking

A mock room-booking app that exists as the test bed for a software factory: a process that turns human intents into merged changes with minimal human intervention.

## Factory

**Intent**:
What the human wants to achieve, expressed as a GitHub issue.
_Avoid_: request, task, requirement

**Plan**:
The agreed artifact that makes an Intent concrete: an agent brief if it is small, or a Spec with its Work items if it is large; it includes the Contract if the Intent changes the API.
_Avoid_: design, proposal

**Spec**:
The issue that describes what a large Intent delivers, including its full Contract, and that parents its Work items.
_Avoid_: PRD, epic, design doc

**Contract**:
The OpenAPI definition of the API a Plan changes, agreed at Alignment before building; only the first Work item materializes it and the rest implement it without modifying it.
_Avoid_: API spec, interface, schema

**Work item**:
A unit of the Plan that an implementer resolves in a single clean-context session.
_Avoid_: ticket, task, sub-issue

**Alignment**:
The moment the human accepts the Plan by applying `ready-for-agent`; from then on the work is delegable.
_Avoid_: approval, sign-off

**Ambiguity**:
A decision the Plan needs that is not settled by the glossary, the ADRs, the existing tests or the coding standards; the only thing that should require the human to align.
_Avoid_: doubt, blocker

**Autonomy level**:
The rung (0–7, from the "from a line to a loop" proposal) that indicates how much of the path the factory covers without a human: 0 Single player, 1 Align before building, 2 Don't grade your own homework, 3 Reuse first, 4 Earn trust, 5 Merged is not done, 6 The client is data, 7 Close the loop.
_Avoid_: maturity, phase

**Audit**:
The human after-the-fact review of a sample of auto-merged changes to judge whether they are understandable; its sampling rate is the confidence indicator.
_Avoid_: review

**Deployment**:
The ephemeral start-up of the `main` version on the CI runner, against which the E2E tests run before it is torn down.
_Avoid_: deploy, release, environment

**Reversion**:
A merged change that had to be undone or that caused a bug linked to it.
_Avoid_: rollback, regression, escape

## Booking

**Room**:
A bookable space with a capacity and Equipment; it is either active or deactivated, and a deactivated Room accepts no new Bookings.
_Avoid_: space, resource

**Equipment**:
The gear a Room has (projector, whiteboard, video conferencing…) that matters when choosing where to book.
_Avoid_: material, resources, extras

**Booking**:
The occupation of a Room by a user during a Time slot, for a number of attendees; it is either active or cancelled, and a cancelled Booking no longer occupies its Time slot.
_Avoid_: reservation, appointment, event

**Time slot**:
The time interval, start inclusive and end exclusive, that a Booking occupies; two adjacent Time slots do not overlap.
_Avoid_: slot, period, window

**Opening hours**:
The weekly period, common to all Rooms, within which every Time slot must fit.
_Avoid_: business hours, availability

**Employee**:
A user who books Rooms and can only cancel their own Bookings.
_Avoid_: user, customer

**Administrator**:
A user who manages Rooms and their Equipment and can cancel any Booking.
_Avoid_: admin, manager
