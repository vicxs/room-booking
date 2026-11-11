# The factory metrics (docs/factory.md, "Metrics") of every measured Intent,
# from the GitHub data scripts/factory-metrics gathers. Run with `jq -n -f`:
#   --arg botLogin          the bot's GitHub login (scripts/bot-identity.sh)
#   --arg botEmail          the bot's commit email (scripts/bot-identity.sh)
#   --slurpfile prs         the merged PRs that close Intents
#   --slurpfile reverts     the merged `Revert "…"` PRs
#   --slurpfile reversionIssues  the issues labelled `reversion`
#   --slurpfile issues      the measured Intents: {number, title, createdAt,
#                           prs (the merged PRs closing it), timeline}
#   --slurpfile prTimelines {number, timeline} of each PR closing an Intent

$prs[0] as $prs | $reverts[0] as $reverts | $reversionIssues[0] as $reversionIssues

| def ts: fromdateiso8601;
def hours($from; $to): (($to | ts) - ($from | ts)) / 3600 * 10 | round / 10;
# Human is any actor other than our bot, other bots included: one rule for
# every source, the bot's login for events and its email for commits.
def human: .login != $botLogin;

# Each human action in a timeline, as {at}: a review (its inline comments and
# thread replies are part of a review: GitHub wraps each reply in a review of
# its own), a comment, or a commit not authored by the bot. A review carries
# its `id` so that dropping the final approval (pr_actions) drops only that
# review, never another one equal to it in time and state.
def actions:
  .[]
  | if .event == "reviewed" and .state != "pending" and (.user | human) then {at: .submitted_at, review: .state, id}
    elif .event == "commented" and (.actor | human) then {at: .created_at}
    elif .event == "committed" and .author.email != $botEmail then {at: .committer.date}
    else empty end;

# A merged PR's actions, without the final approval that precedes its merge.
def pr_actions($pr):
  [ $prTimelines[] | select(.number == $pr.number) | .timeline | actions | select(.at <= $pr.mergedAt) ]
  | (map(select(.review == "approved")) | max_by(.at)) as $final
  | map(select(. != $final));

def intents_of_prs($numbers):
  [ $prs[] | select(.number as $n | $numbers | index($n)) | .closingIssuesReferences[].number ] | unique;

# Each Reversion, as the Intents of the PRs it reverts or that caused it.
( [ $reverts[] | [ (.body // "") | scan("Reverts [^\\s#]*#([0-9]+)") | .[0] | tonumber ] ]
  + [ $reversionIssues[] | [ (.body // "") | scan("Caused by [^\\s#]*#([0-9]+)"; "i") | .[0] | tonumber ] ]
  | map(intents_of_prs(.)) ) as $reversions

| [ $issues[] as $issue
  | [ $prs[] | select(.number as $n | $issue.prs | index($n)) ] as $closing
  | ($closing | map(.mergedAt) | max) as $lastMerge
  | [ $issue.timeline[]
      | select(.event == "labeled" and .label.name == "ready-for-agent" and .created_at <= $lastMerge) ]
    as $alignments
  | ($alignments | map(.created_at) | max) as $alignment
  # Re-Alignment by the human is a touch: it happens at Alignment, not after.
  | ($alignments | sort_by(.created_at) | .[1:] | map(select(.actor | human)) | length) as $realignments
  | ([ $issue.timeline | actions ] + [ $closing[] as $pr | pr_actions($pr)[] ]
     | map(select(.at > $alignment and .at <= $lastMerge)) | length + $realignments) as $touches
  | { issue: $issue.number,
      title: $issue.title,
      prs: $issue.prs,
      leadTimeHours: hours($issue.createdAt; $lastMerge),
      unattendedTimeHours: (if $alignment then hours($alignment; $lastMerge) else null end),
      humanTouches: (if $alignment then $touches else null end),
      reversions: ($reversions | map(select(index($issue.number))) | length) } ]
| sort_by(.issue)
