# Didban UI redesign — approved direction and Android implementation

**Status:** visually approved by the owner on 2026-09-19. The Android implementation now follows this direction; see [implementation and verification](android-redesign.md). The HTML below remains the original visual reference, not an Android runtime or a live service.

## Direction

- Calm, light-first, Persian/RTL-first interface. Equivalent dark mode; keep existing users' explicit theme choice when implementing a new-install light default.
- Four persistent primary destinations: **Servers · Monitoring · Tools · Settings**.
- One obvious primary action per screen; short explanatory copy; persistent contextual Help.
- Server-specific actions stay with their selected server. Docker, processes, services, SSH and files are reached from that server's details, not duplicate server-management destinations.
- Server details become a full-page view of the same server workspace rather than stacked sheets. Preserve canonical route compatibility, real Back behavior and existing server scope.
- Secondary configuration is progressively disclosed; ready-list scan defaults and optional manual/file input remain available.

## Three principal examples

1. **My servers**: search, small status filters, clear name/host/status, CPU and memory only when measured. Missing measurements are not displayed as healthy or 0%. A card opens that server's full details.
2. **Server details**: selected server identity, concise resource cards and labelled tool tiles. No silent switch to another server. Ordinary navigation uses pages; modal confirmation remains appropriate for destructive actions and help.
3. **Cloudflare IP scanner**: ready list versus manual input; selectable official ranges; scan count; collapsed advanced settings; one Start/Stop action; results beneath it. Clearly distinguish candidates and actual results.

The HTML prototype also provides lightweight Monitoring, Tools and Settings views to demonstrate the bottom-navigation structure, not completed Android screens.

## Tokens / behavior

- Light background `#F2F5FA`, cards `#FFFFFF`, text `#19263C`, secondary text `#5C6B81`.
- Action/selection blue `#245BDB`; status colors are reserved for actual meaning (green connected, red failed, gray unknown).
- Thin borders, restrained shadows, 10–16px component radii, consistent spacing and the existing bundled Vazir font family.
- Dark background `#101724`, cards `#182234`, text `#EDF2FA`, action blue `#9ABAFF` with dark button text.
- Final Android implementation must use scalable typography, accessible touch targets, visible focus/state labels and large-font tests. HTML pixel dimensions are a visual reference, not Android dp/sp specifications.

## Approval artifact

The self-contained [interactive HTML proposal](ui-redesign-v1.html) is also versioned here. Open the downloaded HTML in a browser.

Delivered in the shared workspace:
- `deliverables/didban-redesign-v1.html` — self-contained interactive three-screen board, embedded fonts/SVG icons; no CDN or network needed.
- `deliverables/didban-redesign-v1.png` — static overview.
- `review/design-preview-verification.json` — browser checks.

All server names, measurements and scan results in the proposal are **explicit demo data**. The simulation sends no probes, opens no SSH sessions and invokes no Agent routes. Buttons for not-yet-designed tools explain this rather than pretending to perform an operation. The browser preview never asks for real credentials.

Browser verification: initial screens, scoped server navigation, help and Escape dismissal, advanced-settings expansion, per-range selection persistence, clearly labelled scan simulation, theme toggle, search and document overflow at 320/390/768/1440px. No JavaScript errors or external requests were observed.

## Preservation requirements

Existing saved servers, optional manual scanner input and file import, candidate-only claims, pinned TLS, encrypted/masked secrets, RAM-only drafts, dirty-form/discard guards, confirmation of destructive actions, actual refresh scope, sole-server Radar selection, notification permission behavior and default-off screenshot protection must not regress. Do not silently change network engines, data models or Agent compatibility while restyling screens.
