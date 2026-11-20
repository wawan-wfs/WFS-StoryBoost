# Beta12 — caption wrapping and silent export coverage

Verified main `cd8ad45535078f80c79876bdde8adb4c11dca378`. This candidate extends verified beta11 `6faa693921cc2387116e6f62114b0dac96e193d4`, which passed [all four PR jobs](https://github.com/wawanwfs/WFS-StoryBoost/actions/runs/43614145637/attempts/2). Draft PRs #8–#12 are already represented in the ancestor chain, not parallel changes to cherry-pick again. They and main are unchanged.

- Caption layout prefers natural line breaks, balances line widths, and uses Unicode grapheme boundaries when a long token has no useful line break. Its two-line limit and centered safe-zone placement remain. Whitespace-only captions are treated as empty so they cannot defeat original-MP4 passthrough.
- Decoded-frame matrix adds WhatsApp Status, Instagram Story and TikTok at 1080×1920, custom 432×1008 and 1008×432, and a caption-free control. It counts real colored text rows, checks preview bounds/safe margins, verifies track/sample presence or absence (muted and originally silent), and checks source SHA. The earlier manual/Reels rotation, byte-for-byte passthrough, Gallery, cancel and SAF/provider tests remain.
- Safe zones are the app's preset guides, not a guarantee about third-party UI or recompression.
- Physical Android, TalkBack, Google Photos, consented portrait/low-light QA and install-over-upgrade with the owner's release signer remain separate. No inputs or device access were available for those gates in the cloud. See the [owner runbook](PHYSICAL_ARM64_QA_WINDOWS.md).

Roadmap remains 23/32 (71.875%). Confirm results against the exact-head workflow.
