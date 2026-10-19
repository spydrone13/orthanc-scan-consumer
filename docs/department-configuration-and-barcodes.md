# Department Configuration and Barcodes

## Overview

Two screens work together to define how items move through the process and how operators record those moves:

| Screen | Purpose |
|---|---|
| **Department configuration** (titled **Lot Stages** in the application) | Defines the departments' process flow: which departments an item may move to next, and the physical locations within each department |
| **Department Barcodes** sheet | Prints that configuration as a barcode sheet for one department, posted at its station. Operators scan a destination instead of typing it |

The configuration is the single source of truth. The scanning rules use it to check every move, and every barcode sheet is generated from it at the moment it's opened. Paper copies of the sheets don't update themselves, so after a configuration change the affected sheets must be reprinted (see [Keeping posted sheets current](#keeping-posted-sheets-current)).

This brings:

- **Speed:** one scan replaces finding and typing a destination.
- **Accuracy:** there are no typos, and no ids that look alike but aren't the same.
- **Process control:** sheets list only the destinations the configured flow allows, and moves outside the flow are flagged for review.

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
| Process / manufacturing engineer, or admin | Maintains the configuration: next departments and physical locations. Prints and posts the barcode sheets |
| Floor operator | Scans the item, then the destination barcode on the posted sheet |
| Supervisor / quality | Reviews moves that were flagged because they didn't follow the configured flow |

## Key concepts

### Departments

A department is one step in the process. Each one has:

- a **name** shown to people, e.g. "Wet Etching";
- an **id** used by the system and printed on barcodes, e.g. `wet-etching`;
- a fixed **position** in process order, which sets the order departments are listed in everywhere.

### Next departments: the process flow

Each department lists the departments an item may move to **directly** from it. Together these lists form the process flow. A department can:

- lead to **one** next department (a straight line), e.g. Intake → Wafer Prep;
- lead to **several** (a branch), e.g. Photolithography → Wet Etching, Dry Etching or Deposition;
- be reached **from several** (a merge), e.g. both Wet Etching and Dry Etching lead to Photoresist Removal;
- have **none**. It's then a **final** department, e.g. Shipped.

A department can't be its own next department. Moves *within* a department (between its physical locations) don't need configuring and are always allowed.

### Physical (WIP) locations

Each department has a list of its own physical locations: benches, racks, tools or bays where items sit. Each one has:

- an **id**, printed on barcodes, e.g. `WET-ETCHING-002`. It can't be changed once created;
- a **description** shown to people, e.g. "Wet Etching 2". If it's left blank, a default is built from the department name and the id's trailing number: `WAFER-PREP-004` in Wafer Prep becomes "Wafer Prep 4".

### Allowed physical locations in a next department

For each next department, you can also limit **which of its physical locations** an item may go to straight from this department:

| Setting | Meaning |
|---|---|
| Any (the default) | The item may go to any physical location in the next department, or just to the department |
| Some | Only the selected physical locations are allowed |
| None | The item may enter the next department, but not a specific physical location in it from here |

Example: Photolithography could allow moves into Deposition only at `DEPOSITION-001`, the tool that accepts work from that department.

### Default process flow

The system starts with these 14 departments, each with three physical locations, e.g. `INTAKE-001` to `INTAKE-003`:

| Department | Next departments |
|---|---|
| Intake | Wafer Prep |
| Wafer Prep | Photolithography |
| Photolithography | Wet Etching, Dry Etching, Deposition |
| Wet Etching | Photoresist Removal |
| Dry Etching | Photoresist Removal |
| Photoresist Removal | Ion Implantation |
| Deposition | Liftoff |
| Liftoff | Ion Implantation |
| Ion Implantation | Metallization |
| Metallization | Testing |
| Testing | Packaging |
| Packaging | Shipping |
| Shipping | Shipped |
| Shipped | *(final department)* |

## Viewing the configuration

Open the department configuration screen at `/lot-stages`. It's titled **Lot Stages**, and it shows one row per department in process order:

| Column | What it shows |
|---|---|
| **Stage** | The department name, with its id in small grey monospace text underneath |
| **Valid next stages** | One button ("chip") per next department, or a green **Final stage** badge if there are none |
| **WIP locations** | One chip per physical location, showing its description (hover to see the id), or "None" |
| *(actions)* | **Barcodes** opens the department's barcode sheet. **Edit** opens the department for editing |

Reading the next-department chips:

- **Click a chip** to jump to that department's row. It scrolls into view and briefly highlights. If the filter is hiding it, the filter is cleared first.
- A note such as **"· 2 of 3 WIP"** on a chip means the allowed physical locations in that next department are restricted (here, 2 of its 3). Hover over the chip to see which ones, or "No WIP locations allowed" if the department only is allowed.
- No note means any physical location is allowed.

**Filtering:** type in **"Filter by stage or WIP location"** to show only departments whose name or id, next-department names, or physical-location ids or descriptions contain the text. The count beside it shows e.g. "14 stages" or "3 of 14 stages".

## Editing a department

Only one department can be edited at a time. While it's open, the other rows' **Edit** buttons are disabled.

1. Click **Edit** on the department's row. The row expands into an editing form.
2. **Choose the next departments.** The **Valid next stages** column now lists every other department with a tick box. Tick the departments items may move to directly from this one, and untick any that are no longer allowed.
3. **Optionally restrict the physical locations in each next department.** Under each ticked next department (one that has physical locations) is an **"Any WIP location"** box, ticked by default.
   - Leave it ticked to allow any physical location there.
   - Untick it to show that department's physical locations, all pre-ticked, then untick the ones that shouldn't be allowed. Untick them all to allow the department only.
4. **Maintain this department's own physical locations** in the **WIP locations** column:
   - **Change a description:** edit the text next to the id. Clear it to go back to the default, which is shown as a grey placeholder.
   - **Remove a location:** click its **×**.
   - **Add a location:** type a new id (the placeholder suggests the pattern, e.g. `PHOTOLITHOGRAPHY-004`) and, optionally, a description. Then click **Add** or press **Enter**. Use only letters, digits and dashes in ids, since they're printed as barcodes.
   - An existing location's id can't be edited. To change an id, remove the location and add a new one.
5. Click **Save**. The button shows "Saving…".
   - **If it succeeds**, the row returns to normal view, is briefly highlighted, and the change takes effect immediately.
   - **If it fails**, "Could not save: <reason>" appears under the form and your edits are kept, so you can correct them and save again.
6. To abandon your changes, click **Cancel** or press **Esc**. Nothing is saved.

Next departments are always saved in process order, whatever order you ticked them in. That's also the order they appear in on the barcode sheet.

### Messages when editing

| Message | Meaning | What to do |
|---|---|---|
| "<id> is already a WIP location for this stage." | You tried to add a physical location whose id already exists in this department (case is ignored) | Use a different id, or edit the existing location's description |
| "Could not save: the server could not be reached" | The application is down or the network is unavailable | Check the connection and save again. Your edits are kept |
| "Could not save: Duplicate WIP location: <id>" | Two physical locations in the list have the same id | Remove one |
| "Could not save: Duplicate next stage: <id>" | The same next department was submitted twice | Cancel, reopen the department and try again |
| "Could not save: A stage cannot be its own next stage" | The department was listed as its own next department | Untick it |
| "Could not save: Unknown next stage: <id>" | A next department no longer exists | Reload the page and try again |
| "Could not save: <id> is not a next stage" | A physical-location restriction was set for a department that isn't ticked as a next department | Reload the page and try again |
| "Could not save: Unknown WIP location <id> in stage <id>" | An allowed physical location no longer exists in that next department, e.g. someone else just removed it | Reload the page and try again |
| "Could not save: Unknown stage: <id>" | The department being edited no longer exists | Reload the page |

Most of the messages that start with "Could not save" can't come from normal use of the screen. They usually mean the configuration was changed elsewhere while you were editing, and reloading the page fixes them.

### What can't be changed on this screen

- **Departments themselves.** You can't add, remove, rename or reorder departments, or change their ids. The departments and their order come from the initial configuration loaded when the system is first set up. Changing them is a technical change (see [Technical details](#technical-details)).
- **A physical location's id.** Only its description can change.

## How the configuration affects scanning

Saved changes apply to every scan processed **after** the save. There's no restart or separate publish step.

When an operator scans an item and a destination, the system checks the move against the configuration:

| Situation | Result |
|---|---|
| Move to a next department, or to an allowed physical location in one | Recorded normally |
| Move to a physical location within the department being scanned from | Recorded normally. Moves within a department are always allowed, even to a physical location that isn't in its list |
| Move to a department that isn't a next department | **Recorded, but flagged off-route** for review |
| Move to a physical location that isn't allowed from here, or doesn't exist in that department | **Recorded, but flagged off-route** for review |
| Item is on hold, and the move would take it out of its department | **Rejected**: the item stays where it was |
| Item is no longer active (completed, canceled or destroyed) | **Rejected**: the item stays where it was |

Off-route moves are still recorded because the scan reflects where the item physically went. The flag lets a supervisor confirm whether it was a genuine exception, a mistake, or a sign the configuration needs updating.

The system also treats **the department being scanned from as where the item physically is**. If the records showed the item in a different department, they're corrected, and the correction is flagged for review.

Removing a physical location:

- doesn't move items that are already there. Their recorded location stays as it is;
- automatically removes it from other departments' allowed lists, so it disappears from their barcode sheets;
- means later moves into it from another department are flagged off-route.

## Change management

Configuration changes affect the shop floor immediately, so:

- **Limit who edits.** Agree on a small group of owners. The screen itself doesn't restrict access (see [Known gaps](#known-gaps-and-open-items)).
- **Agree changes with the floor first**, especially removing next departments or physical locations, since operators' moves start being flagged as soon as you save.
- **Coordinate edits.** If two people edit the same department at the same time, the last save wins, with no warning.
- **Keep your own record.** The system keeps no history of configuration changes, so note what changed, when and why, e.g. on this Confluence page.
- **Reprint the affected barcode sheets** after every change (see below).

## Department Barcodes sheet

### How to get there

1. On the department configuration screen (**Lot Stages**), find the department.
2. Click **Barcodes** on its row. The sheet opens in a new browser tab.

You can also open a sheet directly: `/lot-stages/barcodes?stage=<department-id>`, e.g. `/lot-stages/barcodes?stage=photolithography`.

> **No Barcodes button?** It's only shown for departments with something to print, meaning at least one next department or physical location.

### What's on a sheet

The page heading reads **"Barcodes — <department name>"**. Below it, the labels are grouped into sections in this order:

1. **This department's own physical locations.** The section is headed **"WIP Locations"** and is used for moves *within* the department. It's left out if the department has none.
2. **One section per next department**, in process order, headed **"Next Stage: <department name>"**. Each section starts with a label for the next department itself, followed by labels for its physical locations. Only the allowed ones are printed if a restriction is set, and none if only the department is allowed.

Every label has three parts:

| Part | Physical location label | Next department label |
|---|---|---|
| Title above the bars (for people) | The location's description, e.g. "Wet Etching 2" (or its id, if it has no description) | The department's name, e.g. "Wet Etching" |
| Barcode (for the scanner) | The location id, e.g. `WET-ETCHING-002` (see [Technical details](#technical-details)) | The department id, e.g. `wet-etching` |
| Text under the bars | The location id, `WET-ETCHING-002` | The department id, `wet-etching` |

Scanning a **department** label moves the item into that department without picking a physical location. Scanning a **physical location** label moves it into that specific location, and so into the department that owns it.

#### Example: the Photolithography sheet

| Section | Labels (title / id) |
|---|---|
| WIP Locations | Photolithography 1 / `PHOTOLITHOGRAPHY-001` · Photolithography 2 / `PHOTOLITHOGRAPHY-002` · Photolithography 3 / `PHOTOLITHOGRAPHY-003` |
| Next Stage: Wet Etching | Wet Etching / `wet-etching` · Wet Etching 1 / `WET-ETCHING-001` · Wet Etching 2 / `WET-ETCHING-002` · Wet Etching 3 / `WET-ETCHING-003` |
| Next Stage: Dry Etching | Dry Etching / `dry-etching` · Dry Etching 1 / `DRY-ETCHING-001` · … |
| Next Stage: Deposition | Deposition / `deposition` · Deposition 1 / `DEPOSITION-001` · … |

### Controlling what's printed

Nothing is configured on the sheet itself. It reflects the department's configuration (see [Editing a department](#editing-a-department)):

| Configuration | Effect on the sheet |
|---|---|
| Next departments ticked | One "Next Stage" section per next department |
| **Any WIP location** ticked for a next department | All of its physical locations are printed |
| Only some of its physical locations allowed | Only those are printed |
| None of its physical locations allowed | Only the next department's own label is printed |
| This department's own physical locations | The "WIP Locations" section |

### Keeping posted sheets current

Reprint and replace a department's sheet whenever any of these change:

- its next departments, or which of their physical locations are allowed;
- its own physical locations, or their descriptions;
- the physical locations or descriptions of any of its **next** departments, since those are printed on its sheet too.

For example, adding `WET-ETCHING-004` to Wet Etching means reprinting the sheets for Wet Etching, and for Photolithography because Wet Etching is one of its next departments.

### Printing

1. Wait for the sheet to finish loading. The **Print** button only appears once the labels are on screen.
2. Click **Print**, or use the browser's print command.
3. The printout leaves out the toolbar and the intro text, puts **two labels per row**, and never splits a label across a page break.

Tips:

- Print at **100% scale**. Shrinking the page ("fit to page") makes the bars narrower and can make labels harder to scan.
- Use a laser printer or a high-quality inkjet setting. Smudged or faded bars scan poorly.
- **Test-scan at least one label** from each new printout before posting it.
- Laminating or using a sheet protector is fine, but avoid glossy covers that reflect the scanner's light.

### Using the barcodes on the floor

1. The operator, working in the scanning application at their department, scans or enters the **item**.
2. They scan the **destination** barcode from the posted sheet.
3. The move is checked and recorded as described in [How the configuration affects scanning](#how-the-configuration-affects-scanning).

### Sheet messages and troubleshooting

| Message on the sheet | What it means | What to do |
|---|---|---|
| "No stage selected. Open this page from the Barcodes button on Lot Stages." | The page was opened without a department in the address | Open it from the **Barcodes** button on the configuration screen |
| "Unknown stage: <id>" | The department id in the address doesn't exist | Check the id, or open it from the configuration screen |
| "The barcode library failed to load." | The browser couldn't load the component that draws the barcodes | Reload the page. If it keeps happening, contact support |
| "Could not load lot stages: …" | The configuration couldn't be fetched, e.g. the server is down or unreachable | Check the connection and try again. If it persists, contact support |
| "This stage has no next stages or WIP locations to print." | The department has nothing to put on a sheet | Add next departments or physical locations, if appropriate |
| "<id> can't be encoded as a barcode" (in place of one label) | That id contains characters a barcode can't hold, e.g. accented or non-Latin letters | Replace the physical location with one whose id uses only plain letters, digits and dashes. The rest of the sheet is unaffected |

## Technical details

### Configuration

| Topic | Detail |
|---|---|
| Screen | `/lot-stages`, served by the orthanc-scan-consumer application (port 3001) |
| Read API | `GET /api/lot-stages` returns all departments in process order, keyed by id |
| Update API | `PUT /api/lot-stages/{id}` replaces one department's next departments, own physical locations and allowed physical locations, and returns the saved department. Responses: 400 for a validation error (message in `detail`), 404 for an unknown department |
| Data fields | `next-stages`: next department ids. `wip-locations`: own physical locations, `{ "<id>": { "description": "…" } }` in display order. `next-wip-locations`: allowed physical locations per next department (missing = any, `[]` = department only) |
| Storage | Database tables `lot_stages`, `lot_stage_next_stages`, `lot_stage_wip_locations`, `lot_stage_restricted_next_stages`, `lot_stage_next_wip_locations` |
| Initial setup | Loaded from `lot-stages.json` (in the application) only when the department table is empty. Adding, removing or renaming departments currently means changing the database or this file and starting from an empty table |
| Where the rules live | `stage/LotStageRoutes.java` (allowed moves) and `lot/domain/Lot.java` (off-route flagging, hold and status rejection) |
| Source files | `static/lot-stages.html`, `static/js/lot-stages.js`, `stage/LotStageController.java`, `stage/LotStageService.java` |

### Barcodes

| Topic | Detail |
|---|---|
| Sheet address | `/lot-stages/barcodes?stage=<department-id>`. The page is read-only and loads its data from `GET /api/lot-stages` |
| Symbology | Code 128 (automatic A/B/C subset) |
| Encoded value | The id wrapped in `%` delimiters, e.g. `%WET-ETCHING-002%`. The delimiters let the scanning application recognize a destination scan. The text under the bars shows the bare id |
| Bar (module) width | 1.5 px, the same on every label so all labels scan the same way. It only shrinks if a long id wouldn't fit the label |
| Bar height / quiet zone | 60 px / 10 px margin on every side |
| Rendering | SVG, scaled by the page without distorting the bars |
| Libraries | JsBarcode v3.12.3 and Bootstrap 5.3.8, both bundled with the application. No internet or CDN access needed |
| Id rules | Ids must be printable ASCII, preferably `A–Z`, `0–9` and `-`. Don't use `%`, because it's the delimiter. Neither rule is currently enforced when saving |
| Source files | `static/lot-stage-barcodes.html`, `static/js/lot-stage-barcodes.js`, `static/css/lot-stages.css`, `stage/LotStagePageConfig.java` |

## Known gaps and open items

| # | Item | Impact | Suggested action |
|---|---|---|---|
| 1 | The scanning application doesn't yet appear to strip the `%` delimiters from a scanned destination | Scanning a new label could submit `%WET-ETCHING-002%` instead of `WET-ETCHING-002`, which wouldn't match a valid destination | Confirm delimiter handling is released in the scanning application before posting new sheets |
| 2 | Sheets printed before the delimiter change hold the bare id | Once the scanning application expects delimiters, old sheets may stop working | Reprint all sheets when the scanning application change is released |
| 3 | The Jira spec (CON-5) still says barcodes hold the raw id | The spec and the implementation disagree | Update CON-5 |
| 4 | Neither screen requires a login | Anyone who can reach the application can change the process flow | Add access control for configuration changes |
| 5 | No protection against simultaneous edits | Two people editing the same department: the last save silently wins | Warn when the department changed since it was opened |
| 6 | No history of configuration changes | You can't see who changed the flow, or when | Add an audit log |
| 7 | Departments can't be added, removed, renamed or reordered from the screen | Process changes need technical help | Add department management, if needed |
| 8 | Ids aren't checked for barcode-safe characters when saved | A bad id is only noticed when its sheet shows "can't be encoded" | Validate ids on save (plain letters, digits, dash) |
| 9 | Removing a physical location doesn't check for items currently in it | Items can be left recorded in a location that no longer exists | Warn, or block removal, while items are there |
| 10 | Moves within a department to a physical location not in its list aren't flagged | A mistyped or retired location inside the same department goes unnoticed | Flag unknown physical locations within a department too |

## Related

- Department configuration screen (**Lot Stages**): `/lot-stages`
- **Dead Letters** screen, for scans that failed processing: `/dead-letters`
- Exceptions API, for flagged scans: `/api/lot-exceptions`
- Jira: CON-3 (department model and initial setup), CON-4 (configuration API), CON-5 (configuration and barcode pages), CON-6 (scanning rules)
