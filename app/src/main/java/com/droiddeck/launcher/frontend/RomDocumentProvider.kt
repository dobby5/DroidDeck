package com.droiddeck.launcher.frontend

import android.content.Context
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import android.provider.DocumentsContract.Root
import android.provider.DocumentsProvider
import android.webkit.MimeTypeMap
import java.io.File
import java.io.FileNotFoundException

class RomDocumentProvider : DocumentsProvider() {
    override fun onCreate() = true

    override fun queryRoots(projection: Array<String>?): Cursor = MatrixCursor(projection ?: arrayOf(Root.COLUMN_ROOT_ID))

    override fun queryChildDocuments(parentDocumentId: String, projection: Array<String>?, sortOrder: String?): Cursor =
        throw FileNotFoundException(parentDocumentId)

    override fun queryDocument(documentId: String, projection: Array<String>?): Cursor {
        val file = fileFor(requireNotNull(context), documentId)
        val columns = projection ?: DOCUMENT_COLUMNS
        return MatrixCursor(columns).apply {
            addRow(columns.map { column ->
                when (column) {
                    Document.COLUMN_DOCUMENT_ID -> documentId
                    Document.COLUMN_DISPLAY_NAME -> file.name
                    Document.COLUMN_MIME_TYPE -> MimeTypeMap.getSingleton().getMimeTypeFromExtension(file.extension.lowercase())
                        ?: "application/octet-stream"
                    Document.COLUMN_FLAGS -> 0
                    Document.COLUMN_SIZE -> file.length()
                    Document.COLUMN_LAST_MODIFIED -> file.lastModified()
                    else -> null
                }
            })
        }
    }

    override fun openDocument(documentId: String, mode: String, signal: CancellationSignal?): ParcelFileDescriptor {
        if (mode != "r") throw FileNotFoundException(documentId)
        return ParcelFileDescriptor.open(fileFor(requireNotNull(context), documentId), ParcelFileDescriptor.MODE_READ_ONLY)
    }

    companion object {
        private val DOCUMENT_COLUMNS = arrayOf(
            Document.COLUMN_DOCUMENT_ID, Document.COLUMN_DISPLAY_NAME, Document.COLUMN_MIME_TYPE,
            Document.COLUMN_FLAGS, Document.COLUMN_SIZE, Document.COLUMN_LAST_MODIFIED,
        )

        fun uri(context: Context, file: File): Uri {
            val document = fileFor(context, file.canonicalPath)
            return DocumentsContract.buildDocumentUri("${context.packageName}.romdocuments", document.path)
        }

        private fun fileFor(context: Context, path: String): File {
            val file = runCatching { File(path).canonicalFile }.getOrNull() ?: throw FileNotFoundException(path)
            if (file.path != path || !file.isFile || RomFolders.all(context).none { root ->
                    runCatching { file.toPath().startsWith(root.canonicalFile.toPath()) }.getOrDefault(false)
                }
            ) throw FileNotFoundException(path)
            return file
        }
    }
}
