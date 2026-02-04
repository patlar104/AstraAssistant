# PR Notes (Feb 3 Baseline + Automation Branch)

This file is a working checklist to carry forward the Feb 3 changes and the in-progress automation work on `codex/automation-gestures`.

## Feb 3 commits (0829ecd, abb4a0e)

Summary:
- Added voice reliability plan, action execution plumbing, and diagnostics.
- Added conversation persistence via Room.
- Added TTS service + state/phase updates.
- Updated Gradle toolchain configuration.

Open follow-ups from that work:
- Implement real STT backends for `CloudSttEngineStub` and `LocalWhisperEngineStub`.
- Wire `AstraAccessibilityService` to observe UI events and feed the brain.
- Validate `AssistantStateStore` transitions during rapid listen/speak cycles.
- Add migration strategy if Room schema evolves beyond v1.

## Automation branch (staged changes)

What’s included:
- New `AccessibilityGestureExecutor` for UI gestures (tap by text/id, scroll, back/home/recents).
- New action steps and confirmation rules for risky UI labels.
- Health state fields for automation availability + last error.
- ViewModel routing between intent actions and UI gestures.

Open follow-ups for this branch:
- Recycle `AccessibilityNodeInfo` instances in tree searches to avoid leaks.
- Confirm action availability (e.g., check `actionList`) before invoking scroll/click.
- Distinguish “accessibility enabled” vs. “service connected” for health UI.
- Add safety checks for ambiguous UI text matches (multiple nodes).

## Testing to run

- Manual: enable accessibility service, try "back", "home", "recents", "scroll down".
- Manual: try tapping visible labels by text and by resource id.
- Manual: turn off accessibility service and confirm health UI + snackbar behavior.
- Regression: verify non-UI actions still go through `AccessibilityActionExecutor`.
