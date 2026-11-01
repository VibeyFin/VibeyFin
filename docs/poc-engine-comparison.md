# Playback engine comparison (issue #26)

Same five titles on each engine, one engine at a time (Settings > playback engine). Engines: AVPlayer and Aether.
Mark each cell `ok`, `fail` or `n/a`, with a note. I read `poc.log` from the phone after each run.

## Titles

| # | Title id | Why it is in |
|---|----------|--------------|
| 1 | `908f3cd5…` | MKV with its index at the end |
| 2 | `e760829e…` | Large MKV, PGS subtitles (Chinese Traditional), starts fast |
| 3 | `50a157b0…` | High bitrate, ASS subtitles |
| 4 | `78dc0547…` | Fell back to transcode once |
| 5 | `a9d67a64…` | MOV container |

## Checks per title

| Check | What counts as ok |
|-------|-------------------|
| Direct play | Log shows `DirectPlay`, no `master.m3u8` |
| Start | Playing within 3 s, no black screen or retry |
| Audio | Sound from the start, switching track works |
| Text subtitles | Selecting one shows cues in time |
| Image subtitles | A PGS track shows (titles that have one) |
| Seek | Jump near the end and back, resumes within 3 s |
| Pause and resume | Controls follow the state |
| Progress bar | Position and length match the real runtime |
| PiP | Starts and keeps playing |

## Results

| Title | Check | AVPlayer | Aether |
|-------|-------|--------|--------|
