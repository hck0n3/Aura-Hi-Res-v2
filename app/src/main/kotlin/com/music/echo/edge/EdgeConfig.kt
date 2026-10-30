package iad1tya.echo.music.edge

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey

/**
 * Phase A of docs/PLAN_WORKER_9_FUNCIONES.md — everything the app asks of Aura's «edge» Worker (docs/worker/edge).
 *
 * Both constants are EMPTY on purpose: with no address or no public key the whole layer is off and the app behaves exactly as
 * before. The owner fills them in at publication time (`npm run keygen` prints the public key); until then only a tester who types
 * the address in Ajustes ▸ Servicios Aura sees anything.
 */
const val EDGE_DEFAULT_URL = ""

/** SPKI (base64) of the ECDSA P-256 key whose private half signs `/v1/config`. A config that does not verify against it is ignored. */
const val EDGE_PUBLIC_KEY = ""

val EdgeUrlKey = stringPreferencesKey("edgeUrl")
val EdgePublicKeyKey = stringPreferencesKey("edgePublicKey")

/** The user's own master switch for every «Servicios Aura» function: always theirs to turn off. */
val EdgeEnabledKey = booleanPreferencesKey("edgeEnabled")
