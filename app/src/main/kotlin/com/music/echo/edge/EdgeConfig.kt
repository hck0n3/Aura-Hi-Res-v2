package iad1tya.echo.music.edge

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey

/**
 * Phase A of docs/PLAN_WORKER_9_FUNCIONES.md — everything the app asks of Aura's «edge» Worker (docs/worker/edge).
 *
 * Owner 2026-10-30: «esto debería ir por defecto, los usuarios no van a estar escribiéndolo cada vez que lo instalen». Both the address
 * of the owner's edge Worker and its public key (public by nature, read from /v1/pubkey and checked against a real signature) are
 * embedded. The field in Ajustes ▸ Servicios Aura only overrides them (someone running their own Worker); blank = the default.
 * What the layer does without any answer from the server is nothing: every function stays on, as before.
 */
const val EDGE_DEFAULT_URL = "https://aura-edge.toberto4000.workers.dev"

/** SPKI (base64) of the ECDSA P-256 key whose private half signs `/v1/config`. A config that does not verify against it is ignored. */
const val EDGE_PUBLIC_KEY = "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEnaLn0WrOTVKV4aVkm53Tw+Wg8x+V87z/twJEtZOn5/9GaMOeoPBo1aQe7dTlsljhH0DJGyZ/rotxTFkt1ijbJg=="

val EdgeUrlKey = stringPreferencesKey("edgeUrl")
val EdgePublicKeyKey = stringPreferencesKey("edgePublicKey")

/** The user's own master switch for every «Servicios Aura» function: always theirs to turn off. */
val EdgeEnabledKey = booleanPreferencesKey("edgeEnabled")

/**
 * Row 606: the smart queue's second opinion by AI (`POST /v1/judge`). ON by default (owner 2026-10-31); it sends the title and artist of what
 * is playing and of the doubtful candidates, nothing else, and only while [EdgeEnabledKey] is also on.
 */
val QueueAiJudgeKey = booleanPreferencesKey("queueAiJudge")
