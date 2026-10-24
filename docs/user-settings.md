# Personal settings and workspace navigation

The header contains the business logo/name on the left and the signed-in member's profile image/name on the right. Its account menu links to **User settings**, **Security & MFA**, **Notification preferences**, theme selection, and Sign out. Plugin authors can add permitted links through the `account.menu` slot.

`/account/settings` has Profile & appearance, Security & MFA, and Notification preferences tabs. Old `/account/security` and `/account/notifications` links redirect to the matching tab. Primary-admin MFA prompts and existing security flows remain available there.

Members can edit their display name, international contact number, and IANA timezone, and upload/remove their own profile image. Login email/username and account roles remain administrator-controlled. Images accept PNG/JPEG/WebP up to 1 MiB and 2048 × 2048 input dimensions, and are normalized to square PNG. Recommend a square 256 × 256 source. Images and timezone/revision live in PostgreSQL `user_profiles`; avatar reads require authentication and return only the current account's image with private/no-store headers. Include this table in database backups. Profile edits reject stale revisions. The timezone is saved for extensions; calendar grids currently use the browser timezone, and job schedules retain their explicit timezone.

Press the header **Collapse navigation / Expand navigation** button to change the sidebar. Its state persists locally per account. Resizing, changing routes, and opening plugins do not automatically collapse it. Each plugin's submenu has a separate 44px expand/collapse control. The navigation list scrolls independently; Administration sits outside that scroll area at the bottom and stays accessible when many plugins are enabled.
