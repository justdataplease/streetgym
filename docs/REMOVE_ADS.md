# Remove ads

Street Gymnastics 2.2.0 adds Settings > Remove ads and Restore purchase. This is one non-consumable Google Play purchase, adapted from Just a Toolbox's Billing 8.3.0 integration. It disables Today cards, workout-section cards and full-screen ad breaks. No subscription or separate account is required by the app.

## Play Console configuration still required

| Setting | Value |
|---|---|
| Application | `com.justdataplease.streetgym` |
| One-time product ID | `remove_ads` |
| Name | Remove ads |
| Description | Remove all ads from Street Gymnastics with one payment. |
| Purchase option | `buy`, non-consumable |
| Requested price | 9.99; use EUR as the local-market starting currency and review regional prices before activation |

The app displays the formatted price returned by Google Play. It never charges a hard-coded amount. The button is disabled until Play returns an available product; the 9.99 placeholder is not an active offer. Product creation, pricing, activation and Play-track distribution have not been performed or verified from this workspace.

The GitHub APK uses `com.justdataplease.streetgym.debug`. That package is separate from the production Play application, so the development download cannot be used to claim a verified production checkout. Keep the established production signing/upload identity when preparing a Play build.

## Purchase behavior

- Only a PURCHASED result for `remove_ads` grants entitlement. Pending and canceled purchases do not unlock it.
- Confirmed purchases are acknowledged, with bounded retries and another attempt on a subsequent successful purchase query.
- The cached entitlement works offline. Connection failures do not revoke it. A successful complete query with no owned product removes the cached entitlement, including after a refund.
- Restore purchase queries the current Google Play account and distinguishes no purchase from an unavailable connection.
- Existing native views are destroyed when ownership changes. A paid user does not prefetch or display the full-screen placement.

This follows Toolbox's client-side entitlement model. No new payment server or server-side purchase verification was added.

## Acceptance before selling

Using a Play license tester and the registered package, verify: successful purchase and acknowledgement, cancellation, pending payment, reinstall/restore, offline ownership, refund, localized pricing, and removal of every placement. Do not use a real paid transaction for a development test. Local unit tests cover entitlement reconciliation; Android checks cover cached paid UI/ad suppression. Those checks do not constitute an actual Google Play transaction.

Primary references: [Google Play Billing integration](https://developer.android.com/google/play/billing/integrate) and [billing testing](https://developer.android.com/google/play/billing/test).
