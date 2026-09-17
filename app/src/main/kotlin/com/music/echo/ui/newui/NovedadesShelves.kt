package iad1tya.echo.music.ui.newui

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
}
