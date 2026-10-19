# Department Barcodes

## Overview

The **Department Barcodes** screen produces a printable barcode sheet for a single department. Each sheet is posted at its department's station. When an operator moves an item, they scan a barcode from the sheet to say where it's going, rather than typing or choosing the destination by hand.

This helps in three ways:

- **Speed:** one scan replaces finding and typing a destination.
- **Accuracy:** there are no typos, and no ids that look alike but aren't the same.
- **Process control:** the sheet lists only the destinations the configured process flow allows from that department, so operators are steered toward valid moves.

The sheet is generated from the department configuration every time it's opened, so it always matches the current setup. Paper copies don't update themselves, though, so after a configuration change the sheet has to be reprinted (see [Keeping posted sheets current](#keeping-posted-sheets-current)).

### Glossary

This page uses generic terms. The application screens, URLs and data use older names, listed here so you can match them up.

| Term on this page | What the application shows | Meaning |
|---|---|---|
| Item | Lot | The thing being tracked and scanned as it moves through the process |
| Department | Stage | A step in the process, e.g. Photolithography |
| Next department | Next stage | A department an item may move to directly from this one |
| Physical (WIP) location | WIP location | A specific place within a department where items wait or are worked on, e.g. "Wet Etching 2" |

## Who uses it

| Role | What they do |
|---|---|
| Process / manufacturing engineer, or admin | Sets up departments, next departments and physical locations, then prints and posts the sheets |
| Floor operator | Scans the item, then the destination barcode on the posted sheet |
| Supervisor / quality | Reviews moves that were flagged because they didn't follow the configured flow |

## How to get there

1. Open the department configuration screen, which the application titles **Lot Stages** (`/lot-stages`).
2. Find the department, using the filter box if needed.
3. Click **Barcodes** on that department's row. The sheet opens in a new browser tab.

You can also open a sheet directly: `/lot-stages/barcodes?stage=<department-id>`, e.g. `/lot-stages/barcodes?stage=photolithography`. The department id is the small grey monospace text under the department's name on the Lot Stages screen.

> **No Barcodes button?** It's only shown for departments with something to print, meaning at least one next department or physical location. A final department with no physical locations has no sheet.

## What's on a sheet

The page heading reads **"Barcodes — <department name>"**. Below it, the labels are grouped into sections in this order:

1. **This department's own physical locations.** The section is headed **"WIP Locations"** and is used for moves *within* the department. It's left out if the department has none.
2. **One section per next department**, in process order, headed **"Next Stage: <department name>"**. Each section starts with a label for the next department itself, followed by labels for that department's physical locations. If the configuration restricts which physical locations are allowed (see [Controlling what's printed](#controlling-whats-printed)), only the allowed ones appear.

Every label has three parts:

| Part | Physical location label | Next department label |
|---|---|---|
| Title above the bars (for people) | The location's description, e.g. "Wet Etching 2" (or its id, if it has no description) | The department's name, e.g. "Wet Etching" |
| Barcode (for the scanner) | The location id, e.g. `WET-ETCHING-002` (see [Technical details](#technical-details)) | The department id, e.g. `wet-etching` |
| Text under the bars | The location id, `WET-ETCHING-002` | The department id, `wet-etching` |

Scanning a **department** label moves the item into that department without picking a physical location. Scanning a **physical location** label moves it into that specific location, and so into the department that owns it.

### Example: the Photolithography sheet

With the default configuration, Photolithography can send items to Wet Etching, Dry Etching or Deposition, and each department has three physical locations. Its sheet contains:

| Section | Labels (title / id) |
|---|---|
| WIP Locations | Photolithography 1 / `PHOTOLITHOGRAPHY-001` · Photolithography 2 / `PHOTOLITHOGRAPHY-002` · Photolithography 3 / `PHOTOLITHOGRAPHY-003` |
| Next Stage: Wet Etching | Wet Etching / `wet-etching` · Wet Etching 1 / `WET-ETCHING-001` · Wet Etching 2 / `WET-ETCHING-002` · Wet Etching 3 / `WET-ETCHING-003` |
| Next Stage: Dry Etching | Dry Etching / `dry-etching` · Dry Etching 1 / `DRY-ETCHING-001` · … |
| Next Stage: Deposition | Deposition / `deposition` · Deposition 1 / `DEPOSITION-001` · … |

## Controlling what's printed

Nothing is configured on the barcode sheet itself. Everything on it comes from the department's settings on the **Lot Stages** screen. To change a sheet, click **Edit** on the department's row, change the settings, then click **Save**.

| Setting on the Lot Stages screen | Effect on the sheet |
|---|---|
| **Valid next stages** (tick boxes): the next departments | One "Next Stage" section per ticked department |
| **Any WIP location**, under each ticked next department, ticked (the default) | Every physical location of that next department is printed |
| **Any WIP location** unticked, then some physical locations ticked | Only the ticked physical locations are printed for that next department |
| **Any WIP location** unticked with nothing ticked | Only the next department's own label is printed: items may enter that department, but not a specific location in it from here |
| **WIP locations** list: this department's own physical locations (add an id and optional description, edit descriptions, remove) | The "WIP Locations" section |

Good to know:

- **Changes take effect immediately**, both for scanning and for any sheet opened afterwards.
- **A physical location's id can't be changed** once it's added; only its description can. To "rename" an id, remove the location and add a new one, then reprint every sheet that showed it, which includes the sheets of departments that list this one as a next department.
- **If a description is left blank**, a default is generated from the department name and the id's trailing number, e.g. `WAFER-PREP-004` in Wafer Prep becomes "Wafer Prep 4".
- **Departments and their names can't be added or changed** on this screen. They come from the initial configuration loaded when the system is first set up.
- Removing a physical location from a department also drops it from other departments' "allowed" lists, so it disappears from their sheets.

### Keeping posted sheets current

Paper sheets don't update themselves. Reprint and replace a department's sheet whenever any of these change:

- its next departments, or which of their physical locations are allowed;
- its own physical locations, or their descriptions;
- the physical locations or descriptions of any of its **next** departments, since those are printed on its sheet too.

## Printing

1. Wait for the sheet to finish loading. The **Print** button only appears once the labels are on screen.
2. Click **Print**, or use the browser's print command.
3. The printout leaves out the toolbar and the intro text, puts **two labels per row**, and never splits a label across a page break.

Tips:

- Print at **100% scale**. Shrinking the page ("fit to page") makes the bars narrower and can make labels harder to scan.
- Use a laser printer or a high-quality inkjet setting. Smudged or faded bars scan poorly.
- **Test-scan at least one label** from each new printout before posting it.
- Laminating or using a sheet protector is fine, but avoid glossy covers that reflect the scanner's light.

## Using the barcodes on the floor

1. The operator, working in the scanning application at their department, scans or enters the **item**.
2. They scan the **destination** barcode from the posted sheet: a physical location in this department, a next department, or a physical location in a next department.
3. The scan is sent for processing, and the item's recorded location is updated.

How the system treats the scan:

- **The department the operator is scanning from is taken as where the item physically is.** If the records showed the item somewhere else, they're corrected, and the correction is flagged for review.
- **Moves that follow the configured flow** (anything on the sheet) are recorded normally.
- **Moves that don't follow the configured flow** are still recorded, because the scan reflects where the item really went, but they're **flagged for review** as off-route. Supervisors see these in the exceptions list.
- **Items on hold** can't be moved out of their current department, and **items that are no longer active** (completed, canceled or destroyed) can't be scanned at all. These scans are rejected and the item stays where it was.

## Messages and troubleshooting

| Message on the sheet | What it means | What to do |
|---|---|---|
| "No stage selected. Open this page from the Barcodes button on Lot Stages." | The page was opened without a department in the address | Open it from the **Barcodes** button on the Lot Stages screen |
| "Unknown stage: <id>" | The department id in the address doesn't exist | Check the id, or open it from the Lot Stages screen |
| "The barcode library failed to load." | The browser couldn't load the component that draws the barcodes | Reload the page. If it keeps happening, contact support |
| "Could not load lot stages: …" | The department configuration couldn't be fetched, e.g. the server is down or unreachable | Check the connection and try again. If it persists, contact support |
| "This stage has no next stages or WIP locations to print." | The department has nothing to put on a sheet | Add next departments or physical locations on the Lot Stages screen, if appropriate |
| "<id> can't be encoded as a barcode" (in place of one label) | That id contains characters a barcode can't hold, e.g. accented or non-Latin letters | Replace the physical location with one whose id uses only plain letters, digits and dashes. The rest of the sheet is unaffected |

## Technical details

| Topic | Detail |
|---|---|
| Page address | `/lot-stages/barcodes?stage=<department-id>`, served by the orthanc-scan-consumer application (port 3001) |
| Data source | `GET /api/lot-stages`, read when the page opens. The page is read-only and never changes data |
| Symbology | Code 128 (automatic A/B/C subset) |
| Encoded value | The id wrapped in `%` delimiters, e.g. `%WET-ETCHING-002%`. The delimiters let the scanning application recognize a destination scan. The text under the bars shows the bare id |
| Bar (module) width | 1.5 px, the same on every label so all labels scan the same way. It only shrinks if a long id wouldn't fit the label |
| Bar height | 60 px |
| Quiet zone | 10 px margin on every side |
| Rendering | SVG, scaled by the page without distorting the bars |
| Barcode library | JsBarcode v3.12.3, bundled with the application. No internet or CDN access needed |
| Styling | Bootstrap 5.3.8, bundled; shared with the Lot Stages screen |
| Id rules | Ids must be printable ASCII, preferably `A–Z`, `0–9` and `-`. Don't use `%`, because it's the delimiter. Neither rule is currently enforced when saving |
| Data fields | Next departments: `next-stages`. Own physical locations: `wip-locations`. Allowed physical locations per next department: `next-wip-locations` (missing = all, `[]` = department label only) |
| Source files | `static/lot-stage-barcodes.html`, `static/js/lot-stage-barcodes.js`, `static/css/lot-stages.css`, `stage/LotStagePageConfig.java` |

## Known gaps and open items

| # | Item | Impact | Suggested action |
|---|---|---|---|
| 1 | Sheets now encode `%id%`, but the Jira spec (CON-5) still says the raw id, with no prefix or suffix | The spec and the implementation disagree | Update CON-5 |
| 2 | The scanning application doesn't yet appear to strip the `%` delimiters from a scanned destination | Scanning a new label could submit `%WET-ETCHING-002%` instead of `WET-ETCHING-002`, which wouldn't match a valid destination | Confirm delimiter handling is released in the scanning application before posting new sheets |
| 3 | Sheets printed before the delimiter change hold the bare id | Once the scanning application expects delimiters, old sheets may stop working | Reprint all sheets when the scanning application change is released |
| 4 | Ids aren't checked for barcode-safe characters when saved | A bad id is only noticed when its sheet shows "can't be encoded" | Validate ids on save (plain letters, digits, dash) |
| 5 | The Lot Stages and barcode screens require no login | Anyone who can reach the application can change the process flow | Add access control for configuration changes |

## Related

- **Lot Stages** (department configuration) screen: `/lot-stages`
- **Dead Letters** screen, for scans that failed processing: `/dead-letters`
- Exceptions API, for flagged scans: `/api/lot-exceptions`
- Jira: CON-4 (department configuration API), CON-5 (configuration and barcode pages)
