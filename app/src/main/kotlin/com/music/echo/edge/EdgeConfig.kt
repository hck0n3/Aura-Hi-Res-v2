package iad1tya.echo.music.edge

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey

/**
 * Phase A of docs/PLAN_WORKER_9_FUNCIONES.md — everything the app asks of Aura's «edge» Worker (docs/worker/edge).
 *
 * The address is EMPTY on purpose: with no address the whole layer is off and the app behaves exactly as before; only someone who
 * types the address in Ajustes ▸ Servicios Aura sees anything. The public key (public by nature, read from the owner's deployed
 * Worker at /v1/pubkey and checked against a real signature) is embedded; the address is the owner's call at publication time.
 */
const val EDGE_DEFAULT_URL = ""

/** SPKI (base64) of the ECDSA P-256 key whose private half signs `/v1/config`. A config that does not verify against it is ignored. */
const val EDGE_PUBLIC_KEY = "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEnaLn0WrOTVKV4aVkm53Tw+Wg8x+V87z/twJEtZOn5/9GaMOeoPBo1aQe7dTlsljhH0DJGyZ/rotxTFkt1ijbJg=="

val EdgeUrlKey = stringPreferencesKey("edgeUrl")
val EdgePublicKeyKey = stringPreferencesKey("edgePublicKey")

/** The user's own master switch for every «Servicios Aura» function: always theirs to turn off. */
val EdgeEnabledKey = booleanPreferencesKey("edgeEnabled")
