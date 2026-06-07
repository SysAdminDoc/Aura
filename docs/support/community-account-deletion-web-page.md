# Aura Community Account Deletion Request

Use this page when you cannot open Aura but need to request deletion of your
Aura community identity and associated community data.

## What You Need

- Your `AURA-` deletion request code from Aura Settings or a prior support
  reply.
- A reply contact so support can verify and respond to the request.
- A short statement asking support to delete your Aura community identity and
  associated community data.

Do not include secrets, private backend files, or internal account identifiers
in the request.

## Form Fields

- `requestCode`: required `AURA-` deletion request code.
- `contact`: required reply contact for private support handling.
- `requesterStatement`: required deletion request statement.
- `attestations.deleteCommunityIdentity`: required confirmation that you are
  requesting community identity deletion.
- `attestations.understandsRetainedRecords`: required confirmation that private
  moderation, rights, safety, and abuse-prevention records may be retained.
- `attestations.understandsPublicUploadsSeparate`: required confirmation that
  public uploads and Storage objects may need separate owner/admin deletion
  handling.

## What Happens Next

Support verifies the request privately, maps the `AURA-` request code to the
matching community identity, removes eligible backend marker data through the
guarded deletion process, and replies with a completion receipt when the
backend deletion is complete.

If you also ask support to remove public uploads, those uploads go through a
separate owner/admin workflow so public metadata, Storage objects, owner
indexes, and deletion evidence stay consistent.

## Retained Records

Aura may retain private moderation, rights, safety, abuse-prevention, report,
and deletion evidence when needed to protect the community, handle disputes, or
prevent abuse. Retained records are not public.

## Privacy Policy

Review the Aura privacy policy before submitting the request. The hosted page
must link to the current privacy policy and must not send users back to the app
as the only way to request deletion.
