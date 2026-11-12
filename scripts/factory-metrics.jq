# The factory metrics (docs/factory.md, "Metrics") of every measured Intent,
# from the GitHub data scripts/factory-metrics gathers. Run with `jq -n -f`:
#   --arg botLogin          the bot's GitHub login (scripts/bot-identity.sh)
#   --arg botEmail          the bot's commit email (scripts/bot-identity.sh)
#   --slurpfile prs         the merged PRs that close Intents
#   --slurpfile reverts     the merged `Revert "…"` PRs
#   --slurpfile reversionIssues  the issues labelled `reversion`
#   --slurpfile issues      the measured Intents: {number, title, createdAt,
#                           prs (the merged PRs closing it), timeline}
#   --slurpfile prActivity  {number, timeline, reviewComments} of each PR
#                           closing an Intent

$prs[0] as $prs | $reverts[0] as $reverts | $reversionIssues[0] as $reversionIssues

| def ts: fromdateiso8601;
def hours($from; $to): (($to | ts) - ($from | ts)) / 3600 * 10 | round / 10;
# Human is any actor other than our bot, other bots included: one rule for
# every source, the bot's login for events and its email for commits.
def human: .login != $botLogin;

# Each human action in a timeline, as {at}: a review, a comment, or a commit
# not authored by the bot. A review carries its `id` so that dropping the
# final approval (pr_actions) drops only that review, never another one equal
# to it in time and state.
def actions:
  .[]
  | if .event == "reviewed" and .state != "pending" and (.user | human) then {at: .submitted_at, review: .state, id}
    elif .event == "commented" and (.actor | human) then {at: .created_at}
    elif .event == "committed" and .author.email != $botEmail then {at: .committer.date}
    else empty end;

# Each human review the timeline leaves out, as {at}. Every review comment
# (an inline comment or a reply in its thread) belongs to a review. A review
# in the timeline already counts once, its inline comments included; but
# GitHub wraps a reply, or a lone line comment, in a review of its own that
# the timeline omits, so that one is counted from its comments.
def omitted_reviews($timeline):
  ($timeline | map(select(.event == "reviewed") | .id)) as $listed
  | map(select(.user | human))
  | group_by(.pull_request_review_id)[]
  | select(.[0].pull_request_review_id as $id | $listed | index($id) | not)
  | {at: map(.created_at) | min};

# A merged PR's actions, without the final approval that precedes its merge.
def pr_actions($pr):
  [ $prActivity[] | select(.number == $pr.number)
    | .timeline as $timeline
    | ($timeline | actions), (.reviewComments | omitted_reviews($timeline))
    | select(.at <= $pr.mergedAt) ]
  | (map(select(.review == "approved")) | max_by(.at)) as $final
  | map(select(. != $final));

# The reverted PRs: those a Revert PR reverts or a reversion issue says caused
# it. Each counts one Reversion, however many of them point at it: GitHub's
# revert button often closes the reversion issue of the same PR.
( [ ($reverts[] | (.body // "") | scan("Reverts [^\\s#]*#([0-9]+)")),
    ($reversionIssues[] | (.body // "") | scan("Caused by [^\\s#]*#([0-9]+)"; "i"))
  | .[0] | tonumber ] | unique ) as $revertedPrs

| [ $issues[] as $issue
  | [ $prs[] | select(.number as $n | $issue.prs | index($n)) ] as $closing
  | ($closing | map(.mergedAt) | max) as $lastMerge
  | [ $issue.timeline[]
      | select(.event == "labeled" and .label.name == "ready-for-agent" and .created_at <= $lastMerge) ]
    as $alignments
  | ($alignments | sort_by(.created_at)) as $alignments
  # Unattended time starts at the last Alignment: re-Alignment restarts it.
  | ($alignments | last | .created_at) as $alignment
  # Human touches count from the first Alignment, so re-aligning an Intent
  # between two of its merged PRs keeps the first PR's touches. Each human
  # re-application of `ready-for-agent` is a touch; it is not an action, since
  # it happens at an Alignment rather than after one.
  | ($alignments | first | .created_at) as $firstAlignment
  | ($alignments | .[1:] | map(select(.actor | human)) | length) as $realignments
  | ([ $issue.timeline | actions ] + [ $closing[] as $pr | pr_actions($pr)[] ]
     | map(select(.at > $firstAlignment and .at <= $lastMerge)) | length + $realignments) as $touches
  | { issue: $issue.number,
      title: $issue.title,
      prs: $issue.prs,
      leadTimeHours: hours($issue.createdAt; $lastMerge),
      unattendedTimeHours: (if $alignment then hours($alignment; $lastMerge) else null end),
      humanTouches: (if $alignment then $touches else null end),
      reversions: ($issue.prs | map(select(. as $n | $revertedPrs | index($n))) | length) } ]
| sort_by(.issue)
