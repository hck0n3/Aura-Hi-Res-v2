package iad1tya.echo.music.reco

import androidx.datastore.preferences.core.intPreferencesKey

/**
 * Registry row 906: the version of the «Tu gusto» AI consent the user accepted ([TasteAiGate.CONSENT_VERSION]); absent = never
 * accepted, or withdrawn. Nothing is sent to an AI for «Tu gusto» unless it equals the current version.
 */
val TasteAiConsentKey = intPreferencesKey("taste_ai_consent_version")
