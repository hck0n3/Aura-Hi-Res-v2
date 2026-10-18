package iad1tya.echo.music.db

import androidx.compose.runtime.Immutable

/**
 * An album or a playlist that has at least one DOWNLOADED song, as the offline body lists it (owner 2026-10-18: *«el
 * modo sin conexión quiero que muestre SIEMPRE las listas y álbumes descargados»*).
 *
 * [downloaded] is the number of songs that really play without a network; [total] is how many the collection has, so a
 * half-downloaded album reads "3 de 12" instead of promising twelve songs that would fail.
 */
@Immutable
data class OfflineCollection(
    val id: String,
    val title: String,
    val thumbnailUrl: String?,
    val downloaded: Int,
    val total: Int,
)
