# The factory

This repo is a lab. The room-booking app is a **mock**: the product does not matter. What is being tested is a **software factory**, a process that turns a human Intent into a merged change with the least possible human intervention, earning confidence through evidence.

It is the test bed for the *"from a line to a loop"* proposal: moving from guiding the agent step by step (*in the loop*) to supervising the system (*on the loop*), one level at a time.

This document is for humans. Agents don't need it: the principles are guaranteed by mechanisms, not by instructions. Vocabulary lives in [`GLOSSARY.md`](../GLOSSARY.md) and decisions in [`docs/adr/`](adr/).

## Principles

| Principle | Mechanism that guarantees it |
|---|---|
| **Propose → Verify → Approve**: the agent proposes, deterministic checks verify, the human approves what is critical | CI as a required check, protected `main` (no direct pushes, neither agent nor human), mandatory CODEOWNER approval, agents acting as a bot identity |
| **Whoever builds never verifies** | `/code-review` runs in clean-context sub-agents that only see the diff and the issue. The verifier provides evidence; it never approves or merges |
| **Confidence is earned with evidence** | [Metrics](#metrics) and sampled Audit |

Constraint for this stage: **we use Matt Pocock's skills as they are**. If a skill doesn't fully fit the process, we note it and leave it for later; we don't modify skills or create new ones.

Noted mismatches:

- `setup-pre-commit` installs Prettier. This repo formats with Biome (frontend) and Spotless (backend), so the hook follows the skill's structure (Husky + lint-staged, then typecheck and tests) with those tools instead.

## Mechanisms in place

What P0 set up, so the principles hold without relying on instructions:

- **Protected `main`**: a repository ruleset with no bypass actors requires a PR, one approving review from a CODEOWNER (`.github/CODEOWNERS`), approval of the last push, resolved conversations and the `identity`, `backend`, `frontend` and `e2e` checks of `.github/workflows/ci.yml`; force pushes and deletion are blocked. The repo is public because rulesets on private repos need a paid plan.
- **CI** (all blocking): Spotless (palantir-java-format) and Error Prone with `-Werror`, unit and Testcontainers integration tests, ArchUnit boundaries, generation from `openapi.yaml` for both sides (the build fails if an implementation drifts from the Contract), Biome, TypeScript, Vitest, Playwright E2E against the production build, and the bot authorship of every PR commit (`identity`).
- **Pre-commit** (`.husky/pre-commit`): formats staged files, then frontend typecheck and unit tests. Slow checks stay in CI.
- **Agent identity**: agents act as the GitHub App `vicxs-room-booking-bot`, never with the human's credentials, so the human can approve their PRs and the metrics can tell agent from human. `scripts/bot-git` (commit and push) and `scripts/bot-gh` (`gh` commands) mint a one-hour installation token from the App's private key in `~/.config/room-booking-bot/`, which never enters the repo. Forgetting the wrappers is detected, not prevented: the `identity` CI job (PRs only) runs `scripts/check-commit-authors`, which fails if any commit the PR adds, merge commits included, is not authored by the bot, lists the offending commits and explains how to re-author them and force-push. Only the author is checked, not the committer; a human clicking "Update branch" is a human touch and fails it, so agents rebase instead. PRs from forks always fail it, by design: their commits are not the bot's. The bot's name and email live in `scripts/bot-identity.sh`, shared by `scripts/bot-git` and the check.
- **Supply chain**: exact dependency versions, pnpm refuses versions published less than a day ago, and GitHub Actions are pinned by commit SHA.

## Autonomy levels

The proposal's scale, and what applies to this mock:

| Level | Name | In the mock |
|---|---|---|
| 0 | Single player | Starting point |
| 1 | Align before building | **Yes**: a Plan with Work items and a Contract, one implementer per Work item |
| 2 | Don't grade your own homework | **Yes**: deterministic checks and independent review |
| 3 | Reuse first, then measure | No (specific to ChainIQ) |
| 4 | Earn trust | Later (P3) |
| 5 | Merged is not done | Later (P4) |
| 6 | The client is data | No |
| 7 | Close the loop | Distant future: automatic Alignment except on Ambiguity |

## Phases

| Phase | What it adds | Level | Human role |
|---|---|---|---|
| **P0 Foundations** | Repo, stack scaffolding, CI (lint, types, tests, ArchUnit, Contract), protected `main`, CODEOWNERS, pre-commit | 0 | Everything |
| **P1 Guided manual** | Skills are invoked locally, Intent by Intent | 1–2 | Align, invoke, approve the PR |
| **P2 Unattended trigger** | GitHub Action on `ready-for-agent` (OAuth auth). Transcripts are uploaded as a run artifact | 1–2 | Align and approve the PR |
| **P3 Earn trust** | Tier router and auto-merge for low risk, with sampled Audit | 4 | Align and audit |
| **P4 Merged is not done** | Ephemeral Deployment in CI with E2E and synthetic traffic | 5 | Same |

Outside the phases, as product Intents when the time comes: migrating to Java 25, Spring Boot 4, React 19 and the current frontend versions ([ADR-0001](adr/0001-java-spring-react-with-deliberately-old-versions.md)), booking by voice with an LLM (it will need its own ADR and evals), and automatic Alignment.

### Phase exit criteria

Evaluated at the retro. To move to the next phase:

- **3 consecutive Intents** completed in the current phase, at least 1 of them large;
- **0 Reversions** in those 3;
- **at most 1 human touch** per Intent after Alignment, not counting the final approval;
- **0 `audit:confusing`** in those 3, from P3 on;
- no blocking retro finding still open.

A Reversion triggers a mandatory retro and **resets the counter to zero**: you don't go back a phase, but you don't move forward either.

## Routes per Intent

Every Intent is an issue and **enters through `/triage`**, which classifies it, recommends a route and waits for the human's decision.

| Type | Route |
|---|---|
| Small | `/triage` → `/implement` (includes `/tdd` and `/code-review`) → `/pr` → human gate |
| Large | `/triage` → `/grill-with-docs` → `/to-spec` (with Contract) → **Alignment** → `/to-tickets` → `/implement-spec` (includes the final review) → `/pr` → human gate |
| Foggy | `/triage` → `/wayfinder` until it clears → large route |
| Bug | `/triage` → `/diagnosing-bugs` (E2E reproduction first) → small route |

An Intent goes down the large route if it has **more than one vertical slice**, **touches more than one Contract endpoint** or has **open decisions**. If a small Intent overflows during `/implement`, the PR is closed, the Intent is redirected to the large route and it becomes a retro finding.

### Contract-first

When an Intent changes the API, the full Contract (OpenAPI) goes in the Spec, or in the brief's *key interfaces* field if the Intent is small, and is reviewed at Alignment. The first Work item materializes it in `openapi.yaml` and generates the Spring interfaces and the TypeScript client; it blocks all the others. Later Work items implement it **without modifying it**: if they need to change it, that is an Ambiguity and the Plan is re-aligned ([ADR-0002](adr/0002-contract-first-with-openapi.md)).

`/implement-spec` works on an integration branch with a single PR. The whole branch is reviewed, read commit by commit: the Contract first, then one merge per Work item.

## Human gate

Until P3, the human approves every PR. From P3 on, auto-merge applies only if all of these hold:

- CI is green;
- `/code-review` reports no hard violations;
- the *Merge Danger* section of `/pr` declares a reversible decision and a low blast radius;
- the PR **does not touch** any of these paths: DB migrations, dependencies (`pom.xml`, `package.json`, lockfiles), `.github/`, `.claude/`, `docs/adr/`, `AGENTS.md`, `CODING_STANDARDS.md`, `GLOSSARY.md`.

If any condition fails, the PR goes to `ready-for-human`. The path list will open up as confidence grows.

## Metrics

Computed from GitHub with a `gh` script (the factory's first Intent) and included in every retro.

| Metric | How it is measured | Required convention |
|---|---|---|
| Lead time | Issue created → PR merged | The PR includes `Closes #N` |
| Unattended time | `ready-for-agent` label → merge | — |
| Human touches | Human comments, reviews and commits on the PR after Alignment | The agent acts as a bot, never as a human user |
| Reversions | `Revert "…"` PRs and issues labelled `reversion` | The issue includes `Caused by #PR` |
| Audit | `audit:ok` / `audit:confusing` labels on merged PRs | From P3 on |

The **Audit sampling rate** is the confidence indicator: if it can be lowered without `audit:confusing` going up, confidence is growing.

## Retro

- **When**: after merging every large Intent and every 3 small ones.
- **Sources**: the session transcripts, including sub-agents' (`~/.claude/projects/<project>/<session>/subagents/`, or the run artifact in P2), the PR, CI failures and the gate's comments.
- **Output**: mechanical mistakes become deterministic checks and judgement calls become rules in `CODING_STANDARDS.md`. Everything lands through a PR reviewed by the human.
- **Also**: updates this document if the process changes and evaluates the phase exit criteria.

## Roadmap

Two tracks, separated by label: **`factory`** (the process, with one Milestone per phase) and **`app`** (the booking features, which feed the factory).
