# Fork management (Psyquix/ReadYou)

This fork tracks upstream with three local additions. Everything below is
designed to survive `track-upstream` merges without manual work.

## What's different from upstream

1. **Feed-icon resolution** (`RssHelper.queryRssIcon`, used at subscribe,
   sync, and manual reload): feed-declared `<image>` first, then the site
   homepage's icons (Twine-style scraping), then the legacy feed-host lookup.
   Upstream only does the last step, so feeds hosted away from the publisher
   (e.g. on GitHub Pages) showed the host's icon.
2. **OOM guard** (`SubscribeViewModel` + `FeedPreview`): the parsed `SyndFeed`
   is kept in a plain ViewModel field, never in compose state. Previously the
   whole feed sat in `SubscribeState.Configure`, and Compose hashing it
   (ROME `toString`, ~2x feed size in memory) crashed large feeds with
   `OutOfMemoryError` on 256 MB-heap devices.
3. **Mark above/below as unread** (`ArticleListReaderViewModel.markAsUnreadFromListByDate`):
   adds the inverse of upstream's positional marks to the long-press article
   menu. Upstream only offers "Mark above/below as read"; this adds the
   matching unread pair, scoped to the currently loaded articles exactly as the
   read pair is. The unread items only appear when there is something read
   above or below the long-pressed article, so on an unread filter the menu
   stays the same length as upstream's. Filled arrows mean "becomes read",
   outlined mean "becomes unread".
4. **Per-feed scroll memory** (`FlowPage` + `FlowScrollRestore` +
   `FlowScrollPositionStore`): each feed/group/filter remembers its article-list
   position (article id + index + offset, DataStore-backed) and restores it
   across restarts, back-navigation, and feed switches. Anchored on the article
   id so new arrivals above do not shift the user; a gone article falls back
   to the clamped saved index. Search results are never stored, mark-all-read
   and filter taps still go to top, and an open article owns the position on
   return. Upstream resets to top on every filter change (by construction) and
   on sync; the sync reset is left in place and restore runs after it.
5. **Mark as read at end of article** (new `MarkAsReadAtEndPreference`, off by
   default; `ReadingPage` + `ArticleListReaderViewModel.markCurrentArticleAsRead`):
   with the setting on, opening an article no longer marks it read — swiping
   to the end of the content does, in both the Native and WebView renderers.
   Short articles already showing their end mark read on open, as before.
   Backing out early leaves the article unread. The one-line gate in `readData`
   is an `app/` exception alongside versioning (see below). Follow-up fixes: the
   WebView body is laid out at full height inside the outer column, so the
   outer scroll is the reading movement — first a premature bridge on the
   WebView's own (near-zero-range) scroll was tried and fully reverted, then
   the end check moved to the outer `ScrollState` with an unmeasured
   (`maxValue` 0) guard, which is what previously marked articles on open.
6. **Update-check retarget** (`update_link` in all 42 locale `strings.xml`):
   the in-app checker queried upstream's `releases/latest`; it now queries
   this fork's. `getString` resolves per device locale, so every locale file
   had to move — not just the default one.
7. **Per-article reading positions** (`ReadingPositionStore` + `ReadingPage`):
   each article remembers where you left off (Native list index/offset, WebView
   outer-scroll offset — the WebView body is laid out at full height, so its
   own scroll never moves and the outer scroll is the signal) in a dedicated
   `reading_positions` DataStore file that loads lazily — main settings and
   startup are untouched. Reopening auto-jumps to the saved spot (polls wait
   for layout, abort if you moved); reaching the end forgets it; scrolling
   back to the top clears it. Positions exist for unread articles only:
   opening a read article starts fresh and drops any stale spot, and the spot
   is forgotten the moment the article becomes read. (`initData`'s hardcoded
   unread flag, which also lied to the BottomBar, now reports the true state
   — third `app/` exception.) Stale entries are pruned on save against the
   article table, and Settings → Interaction offers a confirmed wipe of the
   file.

### Verification status

Being in `main` proves only that it compiles, signs, and passes the unit tests.
It does not prove the behaviour. CI cannot exercise a Compose menu, so each
patch carries a marker for what has actually been seen on a device.

| Patch | Tests | Device-verified |
|---|---|---|
| 1. Feed-icon resolution | — | yes, in the field |
| 2. Large-feed OOM guard | — | reported from the field (256 MB device) |
| 3. Mark above/below as unread | `SelectPositionalArticlesTest`, 8 JVM tests | yes — menu shows and hides as intended |
| 4. Per-feed scroll memory | `FlowScrollPositionTest`, 8 JVM tests | no — restores in CI only so far |
| 5. Mark read at end of article | `MarkReadAtEndTest`, 8 JVM tests | no — end-detection in CI only so far |
| 6. Update-check retarget | — (string resource, no logic) | no |
| 7. Per-article reading positions | `ReadingPositionTest`, 7 JVM tests | no — jumping in CI only so far |

When a patch is merged, its row reads "no" until someone has run it. Promoting
a row to "yes" is a docs commit; nothing enforces it.

### How the patches are shaped

Every change to an upstream-owned file is a pure insertion: new parameters
(defaulted to `null`), new functions, new menu items, new strings. No upstream
function body is modified. `markAsReadFromListByDate` is deliberately left
byte-identical and the unread path is a sibling function, so an upstream
conflict in the article menu can never take the new feature down with it.
Selection logic lives in the pure top-level `selectPositionalArticles`, covered
by `SelectPositionalArticlesTest` (JVM, no emulator).

When adding a patch, keep that property: it is what makes the weekly automated
merge boring. Check it before pushing:

```sh
git diff --numstat -- app/ | awk '{a+=$1; d+=$2} END {print "added="a" deleted="d}'
```

`deleted=0` is the invariant. A non-zero count means an upstream line was
rewritten, and that is the change most likely to conflict on the next merge.
This is checkable, so check it rather than eyeballing the diff.

The one exception so far is a test file, which is fork-owned and not part of
the invariant.

The exceptions are versioning (below) plus two one-line touches in
`ArticleListReaderViewModel`: patch 5's `readData` gate and patch 7's true
unread flag in `initData`.

The second exception is versioning: `versionCode`, `versionName`, and the APK
`outputFileName` in `app/build.gradle.kts` are fork-managed (currently `7` /
`0.6.0` / `ReadYou-<version>-Manual.apk`). Upstream bumps its own version
lines on every release, so these lines conflict on every upstream merge —
that is expected. Resolution is always "keep ours". When an upstream merge
moves the base, also bump `UPSTREAM_BASE` in `release.yml` so the next
release-notes footer names the right upstream version.

## Automation (`.github/workflows/`)

- `track-upstream` (weekly Mondays + manual): merges the latest
  `Ashinch/ReadYou` release into `main` and pushes (which triggers the stock
  `Build Commit` APK build). Opens an issue on merge conflict — resolve by
  hand, keeping both sides where sensible.
- `release` publishes `v0.4.0`-style tags with the APK attached, two ways:
  - **automatically** — on green `Build Commit` runs whose head commit contains
    `merge upstream`
  - **manually** — `workflow_dispatch` with a `source_run` input (a Build Commit
    run id). Tags as `vX.Y.Z` and the notes carry the fork changelog plus a
    footer naming the upstream base (`UPSTREAM_BASE` at the top of
    `release.yml`). A run that did not conclude `success` is refused, so a
    red build cannot be published by hand. The workflow checks out the source
    run's commit, so `versionName` is read from the tree the APK was built from.

  ```sh
  gh workflow run release.yml -f source_run=<Build Commit run id>
  ```
- `build_commit.yaml` (upstream file, lightly edited): pinned to
  `ubuntu-24.04` and Node-24-ready actions (`checkout@v4`, `setup-java@v4`,
  `gradle/actions/setup-gradle@v4`, `upload-artifact@v5`), plus a
  `testGithubReleaseUnitTest` step. `release` only fires on a green
  `Build Commit`, so **unit tests are a release precondition**. This matters
  because `testing.yml` is `pull_request`-only and would otherwise never run on
  an automated upstream merge — a merge that compiles but is semantically wrong
  would ship. Add a test with every patch; this gate is what runs it.
- `testing.yml` (upstream file): unchanged, PR-triggered unit tests.

Committing a patch: keep `merge upstream` out of the commit message, or
`release` will trigger on it.

### A passing test task is not proof that tests ran

Gradle prints no per-test names at default log level, and a test class that is
never discovered still reports `BUILD SUCCESSFUL`. That is not hypothetical: an
early run of this fork's pipeline was green with every test in the suite
silently skipped. `build_commit.yaml` now reads the JUnit XML and fails the
build when no results exist or the count is zero, and publishes the report as
`Unit-Test-Report`. Expect `unit tests: files=N tests=M failures=0 errors=0` in
a healthy run — if that line is missing, the gate did not actually run.

The report artifact is worth opening after adding a patch. A green count says
the tests executed, not that they assert what you think they assert.

## Installing

Releases carry fork-versioned APKs (`ReadYou-0.4.0-Manual.apk` under tag
`v0.4.0`). Migrating from stock requires uninstall (different signature):
export OPML first, then import it in the fork. Migrating from a `0.16.x`
fork build also requires an uninstall (fork version numbering restarted at
`0.4.0`, so the version code went down and Android refuses the update).
Updates within `0.4.x` install over each other, no data loss.

## If upstream moves

Upstream branding already points at `ReadYouApp/ReadYou`. If `Ashinch/ReadYou`
goes stale, retarget `track-upstream.yml` (remote URL + API repo in the
`gh api` call) at the new home.

## Known rough edges

- **Deprecations in `build_commit.yaml`** (upstream's file, left alone
  deliberately): `actions/setup-java@v4` is deprecated, and `checkout@v4`,
  `setup-java@v4` and `setup-gradle@v4` are being forced from Node 20 to
  Node 24. Harmless while the forcing works. Bump them to `setup-java@v5` and
  equivalents before those versions are force-upgraded, as a standalone commit
  unrelated to any patch.
- **Positional marking is scoped to loaded articles.** "Mark above/below as
  unread" affects what paging has loaded, not the whole database, matching
  upstream's read pair exactly. With a long feed, only the loaded window moves.
- **The unread items are gated on the loaded range.** `ArticleList` computes the
  first and last index holding a read article using `peek`, which returns null
  for not-yet-loaded pages. Near a paging boundary this biases toward a shorter
  menu — it can hide the item when there is genuinely something to act on. If
  that shows up in use, widen the scan to the loaded range rather than removing
  the gate.
- **Device verification is manual.** Nothing in CI catches a Compose change
  that compiles and tests but looks or behaves wrong. See the verification
  table above.
- **Scroll memory restores once per list.** If you pull-to-sync while deep in
  a feed, upstream jumps to top and the patch jumps back — a deliberate
  flicker that keeps the change a pure insertion. If you scroll to top
  yourself in the half-second before the jump-back fires, it will yank you
  back down; scroll again and it stays (a scrolled list is never restored
  over). One DataStore entry is kept per feed/group/filter/sort; nothing
  prunes entries for deleted feeds.
- **New upstream locales re-point the updater.** If an upstream merge adds a
  locale, its `update_link` arrives pointing at upstream — swap it to this
  fork on merge, or those devices check the wrong releases.
