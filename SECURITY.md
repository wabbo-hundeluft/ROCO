# Security policy

## Preview scope

The public APK is an experimental development-signed Android preview. It is not a clinical product. Current source and current preview behavior are the primary investigation targets; no fixed security-response SLA is promised.

## Reporting a vulnerability

Do not publish secrets, health records, exploit details or identifying screenshots in public issues. If GitHub offers **Security → Report a vulnerability**, use that private channel. If it is not available, open a minimal issue asking the maintainer for a private contact channel, without vulnerability details, or use a maintainer-provided private contact.

Include affected version, connector/provider category, impact and a minimal synthetic reproduction once a private channel is established. Revoke exposed API keys through their providers. A local deletion does not revoke a remote key.

## Release integrity

Download only from this repository's Releases and compare SHA-256 against the release checksum. A checksum checks file integrity; it does not prove independent security review. Preview builds use a development certificate. Building locally creates a different debug certificate and cannot directly update an APK signed by another key. Never commit signing keys.

A dedicated release-signing policy and independent audit remain future work; no such process is claimed to exist today.
