# Sound shuffle pools

Aura can rotate the system ringtone, notification sound, and alarm sound from
small named pools. A pool can target one sound role or any combination of the
three. Each pool has its own schedule.

## Add sounds

A pool can contain:

- downloaded sounds from the Aura library
- audio chosen with Android's file picker
- Aura Originals included with the app

Local files keep Android's persisted read permission when the document provider
allows it. Aura validates that a chosen file is decodable audio and limits each
item to 64 MB. A pool can contain up to 500 sounds, and Aura stores up to 100
pools.

The scheduler checks duration for each target before applying a sound:

| Target | Accepted duration |
|---|---:|
| Ringtone | 1 to 60 seconds |
| Notification | 0.1 to 10 seconds |
| Alarm | 5 to 60 seconds |

A mixed pool can therefore use a short chime for notifications and a longer
clip for ringtone or alarm changes. Incompatible items remain in the pool and
are recorded as skipped for that target.

## Scheduling and recovery

Each enabled pool uses its own WorkManager schedule. The available intervals
are 1, 6, 12, 24, or 72 hours. Android can delay background work because of
battery policy, Doze, or system quotas.

Aura restores enabled schedules after a reboot or app update. Disabling or
deleting a pool cancels its periodic job and any queued manual run. The worker
does not immediately select the same item again when another compatible item is
available.

Android requires the Modify system settings permission before Aura can change a
system sound. The pool editor shows a recovery action when that permission is
missing.

## Missing files and history

Missing downloads, revoked document permissions, and incompatible clips are
skipped without stopping other valid pool members. The pool member stays
visible. Local and restored downloaded entries offer Relink, which validates
the replacement before changing the saved locator.

The pool editor records applied, skipped, failed, and undone changes. Undo is
available only while the system sound still matches the item Aura applied. If
another app or the user changed it afterward, Aura leaves the newer choice
alone.

## Backups and privacy

Portable library backup format 4 includes pool names, targets, schedules, and
assignments. It does not include audio bytes, private file paths, document URIs,
system sound URIs, or shuffle history.

Aura Originals resolve by their stable bundled IDs after import. Local and
downloaded assignments stay visible as missing until Aura finds the matching
library item or the user relinks the file. Import merges pools without
duplicating an existing pool ID or name.
