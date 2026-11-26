package iad1tya.echo.music.cloud

import android.content.Context
import java.io.File

/*
 * Registry row 888 — the packaged rclone executable (contract with the packaging, rows 886-887): lib/<abi>/librclone.so,
 * extracted to nativeLibraryDir, the only app directory Android 10+ lets it exec from (W^X). An ABI the build has no
 * binary for gets null, and the cloud says «no disponible en este aparato» instead of failing.
 */
object RcloneBinary {
    fun path(context: Context): File? = resolve(context.applicationInfo.nativeLibraryDir)

    /** Pure: [nativeLibraryDir]/librclone.so when it is there and executable, else null. */
    fun resolve(nativeLibraryDir: String?): File? =
        nativeLibraryDir?.let { File(it, RcloneProcessPolicy.BINARY_FILE_NAME) }?.takeIf { it.isFile && it.canExecute() }
}
