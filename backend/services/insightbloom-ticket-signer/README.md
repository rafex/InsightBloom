# Internal ticket signer

Internal Ed25519 signing API for `insightbloom-users`. The pod has no public ingress.
Only `users` is allowed by the GitOps NetworkPolicy and requests require a dedicated
bearer token. The API exposes neither gopass nor private key bytes.

The chart mounts a persistent gopass home and an optional SOPS-managed Secret named
`insightbloom-ticket-signer-secrets` containing `api-token`, `gpg-private-key.asc`,
`gpg-passphrase`, and `gpg-fingerprint` (the GPG recipient fingerprint). At startup the
entrypoint imports the GPG identity into ephemeral pod storage and initializes the gopass
store on the persistent volume if needed. Do not place secret values in image layers or
plaintext repository files. Until the key secret and `TICKET_SIGNER_API_TOKEN` are
provisioned, signing is denied/fails closed. Event entries use UUID-only names under
`insightbloom/ticket-signing/events/`.

`POST /internal/v1/tickets/sign` accepts base64url canonical ticket JSON and the event UUID,
validates that the payload names that event, then returns the key ID, public SPKI and the
COSE Sig_structure Ed25519 signature. It never returns a private key. API docs are disabled.
