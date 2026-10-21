package iad1tya.echo.music.ui.newui

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Cuándo un estante de Novedades merece dibujarse, y por qué había estantes con una sola tarjeta.
 *
 * 🔴 Punto 9 del dueño (2026-09-17): *"eliminar la categoría de 'playlists actualizadas' para evitar
 * contenido repetido. Mantener únicamente la sección de novedades, actualizando diariamente la
 * categoría de 'lo más nuevo' (**asegurando que haya variedad de contenido, no solo un elemento**) y
 * 'las canciones del momento'"*.
 *
 * ## De dónde salía "solo un elemento"
 * Novedades deduplica por **primer id visto gana**, para que el mismo álbum no aparezca en dos
 * estantes. Correcto. El problema es el orden: el HÉROE de arriba se construye como
 * `(radarAlbums + newAlbums).take(8)` y reclama sus ids **primero** — o sea que se come los ocho
 * primeros de exactamente las dos listas que alimentan los estantes "lo más nuevo" y "lanzamientos
 * recientes". Si YouTube devolvió nueve álbumes, al estante le queda **uno**. Y el estante se
 * dibujaba igual, porque su única guarda era `if (albums.isEmpty()) return`.
 *
 * No es un fallo del héroe ni de la deduplicación por separado: es que las dos cosas correctas se
 * suman a una tercera que nadie decidió.
 *
 * ## La regla
 * Un estante con menos de [MIN_SHELF_ITEMS] tarjetas **no se dibuja**. Es preferible que falte la
 * sección a que aparezca un título de categoría con una tarjeta debajo: lo segundo no es variedad, es
 * una sección rota con un encabezado que promete más de lo que hay.
 *
 * Deliberadamente NO se arregla dándole al héroe otra fuente ni sacándolo de la deduplicación: lo
 * primero cambia qué se destaca (una decisión de producto que no me toca), y lo segundo trae de vuelta
 * el contenido repetido entre el héroe y el estante, que es justo lo que este punto pide evitar.
 */
object NovedadesShelves {

    /**
     * Mínimo de tarjetas para que un estante exista.
     *
     * **3 y no 2**: estos estantes son carruseles horizontales, y con dos tarjetas no hay nada que
     * desplazar — se lee igual de roto que con una. Tres es donde el carrusel empieza a parecer un
     * carrusel. Y no más alto, porque subirlo empezaría a esconder secciones que sí tienen contenido
     * legítimo en cuentas o regiones con menos catálogo.
     */
    const val MIN_SHELF_ITEMS = 3

    /** ¿Merece dibujarse un estante con [count] elementos? */
    fun worthShowing(count: Int): Boolean = count >= MIN_SHELF_ITEMS

    /**
     * ¿Se dibuja el estante como REJILLA (todas las tarjetas, en filas) en vez de como carrusel?
     *
     * Sí cuando el usuario lo expandió con «Ver todo», cuando hay un filtro de tipo activo (un resultado
     * filtrado puede tener 1 o 2 tarjetas, y un carrusel con dos tarjetas se lee roto: la rejilla las
     * deja bien colocadas) o cuando el estante, por tener pocas tarjetas, no da para desplazarse.
     */
    fun asGrid(filtered: Boolean, expanded: Boolean, count: Int): Boolean =
        filtered || expanded || count < MIN_SHELF_ITEMS

    /**
     * Qué tan reciente es un estreno, para la píldora «Hoy» / «Ayer» / «Esta semana» / «Este mes».
     * `null` = demasiado viejo o con fecha futura: no se dibuja nada antes que una etiqueta falsa.
     */
    fun ageOf(date: LocalDate, today: LocalDate): ReleaseAge? {
        val days = ChronoUnit.DAYS.between(date, today)
        return when {
            days < 0 -> null
            days == 0L -> ReleaseAge.Today
            days == 1L -> ReleaseAge.Yesterday
            days <= 6L -> ReleaseAge.ThisWeek
            days <= 29L -> ReleaseAge.ThisMonth
            else -> null
        }
    }

    /** ¿Un elemento de este tipo pasa el filtro? «Próximamente» solo enseña su propio estante. */
    fun matches(filter: NovedadesFilter, kind: AuraContentKind): Boolean = when (filter) {
        NovedadesFilter.All -> true
        NovedadesFilter.Albums -> kind == AuraContentKind.Album
        NovedadesFilter.SinglesAndEps -> kind == AuraContentKind.Single || kind == AuraContentKind.Ep
        NovedadesFilter.Songs -> kind == AuraContentKind.Song || kind == AuraContentKind.Video
        NovedadesFilter.Upcoming -> false
    }

    /**
     * Los filtros que se ofrecen: «Todo» y solo los que tienen algo detrás (un chip que siempre deja la
     * pantalla vacía es un botón roto). Con un único filtro posible no hay nada que elegir y la fila no se dibuja.
     */
    fun visibleFilters(
        hasAlbums: Boolean,
        hasSinglesAndEps: Boolean,
        hasSongs: Boolean,
        hasUpcoming: Boolean,
    ): List<NovedadesFilter> {
        val all = buildList {
            add(NovedadesFilter.All)
            if (hasAlbums) add(NovedadesFilter.Albums)
            if (hasSinglesAndEps) add(NovedadesFilter.SinglesAndEps)
            if (hasSongs) add(NovedadesFilter.Songs)
            if (hasUpcoming) add(NovedadesFilter.Upcoming)
        }
        return if (all.size < 3) listOf(NovedadesFilter.All) else all
    }

    /** Filas necesarias para [count] tarjetas en [columns] columnas. */
    fun rowCount(count: Int, columns: Int): Int =
        if (count <= 0 || columns <= 0) 0 else (count + columns - 1) / columns
}

/** Filtro por tipo de la pantalla de Novedades. */
enum class NovedadesFilter { All, Albums, SinglesAndEps, Songs, Upcoming }

/** Antigüedad de un estreno con fecha conocida. */
enum class ReleaseAge { Today, Yesterday, ThisWeek, ThisMonth }
