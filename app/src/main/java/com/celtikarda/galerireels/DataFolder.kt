package com.celtikarda.galerireels

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import android.provider.MediaStore
import java.io.File

/**
 * Kullanıcının seçtiği klasör (Android'in klasör seçme penceresiyle, SAF). Uygulama silinince
 * kendi içindeki her şey de silinir; bu klasör ise telefonda kalır. Uygulama yeniden kurulunca
 * aynı klasör tekrar seçilir ve veriler oradan geri gelir.
 *
 * Seçilen klasörün içinde "GaleriReels" adında bir klasör açılır (seçilen zaten oysa o kullanılır).
 */
class DataFolder(
    /** Seçme penceresinden dönen ve izni alınan klasör */
    val treeUri: Uri,
    /** Verilerin durduğu GaleriReels klasörünün belge kimliği */
    val dirId: String,
) {
    /** Ekranda gösterilecek yol, ör. "Telefon hafızası › Documents › GaleriReels" */
    val displayPath: String get() = readablePath(dirId)

    fun readData(context: Context): String? {
        val uri = findChild(context, DATA_FILE) ?: return null
        return context.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
    }

    fun writeData(context: Context, text: String) = write(context, DATA_FILE, "application/json", text)

    /** Klasörün ne olduğunu anlatan not; zaten varsa dokunulmaz. */
    fun writeReadmeIfMissing(context: Context) {
        if (findChild(context, README_FILE) == null) write(context, README_FILE, "text/plain", README)
    }

    private fun write(context: Context, name: String, mime: String, text: String) {
        val resolver = context.contentResolver
        val uri = findChild(context, name)
            ?: DocumentsContract.createDocument(resolver, DocumentsContract.buildDocumentUriUsingTree(treeUri, dirId), mime, name)
            ?: error("$name oluşturulamadı")
        // "wt": dosyayı baştan yaz (eski içerik kısaysa sonunda artık kalmasın)
        resolver.openOutputStream(uri, "wt")?.use { it.write(text.toByteArray(Charsets.UTF_8)) }
            ?: error("$name yazılamadı")
    }

    private fun findChild(context: Context, name: String): Uri? =
        children(context, treeUri, dirId).firstOrNull { it.name == name }?.let {
            DocumentsContract.buildDocumentUriUsingTree(treeUri, it.id)
        }

    companion object {
        const val DATA_FILE = "galerireels-veri.json"
        private const val README_FILE = "BENİ OKU.txt"
        private const val DIR_NAME = "GaleriReels"

        /**
         * Seçme penceresi açılmadan önce Documents/GaleriReels klasörünü hazırlar (yoksa içine
         * BENİ OKU.txt koyarak açar) ve pencerenin o klasörde açılacağı adresi döner. Böylece
         * kullanıcı sadece bu klasöre izin verir; bütün Documents'a (içindeki başka dosyalara) değil.
         * Dosya yazdığı için ana iş parçacığında çağrılmamalı.
         */
        fun prepareSuggested(context: Context): Uri {
            @Suppress("DEPRECATION") // sadece klasörün var olup olmadığına bakmak için
            val documents = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
            val dir = File(documents, DIR_NAME)
            // Android 10+: uygulamalar Documents içine izinsiz dosya koyabiliyor (klasör de açılır)
            if (!dir.exists() && Build.VERSION.SDK_INT >= 29) {
                runCatching {
                    val values = ContentValues().apply {
                        put(MediaStore.MediaColumns.DISPLAY_NAME, README_FILE)
                        put(MediaStore.MediaColumns.MIME_TYPE, "text/plain")
                        put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_DOCUMENTS}/$DIR_NAME")
                    }
                    val resolver = context.contentResolver
                    resolver.insert(MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), values)?.let { uri ->
                        resolver.openOutputStream(uri)?.use { it.write(README.toByteArray(Charsets.UTF_8)) }
                    }
                }
            }
            val docId = if (dir.exists()) "primary:${Environment.DIRECTORY_DOCUMENTS}/$DIR_NAME" else "primary:${Environment.DIRECTORY_DOCUMENTS}"
            return DocumentsContract.buildDocumentUri("com.android.externalstorage.documents", docId)
        }

        /**
         * Seçilen klasörden veri klasörünü bulur: seçilen klasör GaleriReels'in kendisiyse ya da
         * içinde veri dosyası varsa onu, değilse içindeki GaleriReels klasörünü (yoksa açar).
         * Dosya okuyup yazdığı için ana iş parçacığında çağrılmamalı.
         */
        fun open(context: Context, treeUri: Uri): DataFolder {
            val rootId = DocumentsContract.getTreeDocumentId(treeUri)
            val rootChildren = children(context, treeUri, rootId)
            val rootName = displayName(context, treeUri, rootId)
            if (rootName.equals(DIR_NAME, ignoreCase = true) || rootChildren.any { it.name == DATA_FILE }) {
                return DataFolder(treeUri, rootId)
            }
            val existing = rootChildren.firstOrNull { it.isDir && it.name.equals(DIR_NAME, ignoreCase = true) }
            val dirId = existing?.id ?: run {
                val created = DocumentsContract.createDocument(
                    context.contentResolver,
                    DocumentsContract.buildDocumentUriUsingTree(treeUri, rootId),
                    Document.MIME_TYPE_DIR,
                    DIR_NAME,
                ) ?: error("$DIR_NAME klasörü açılamadı")
                DocumentsContract.getDocumentId(created)
            }
            return DataFolder(treeUri, dirId)
        }

        /** Daha önce seçilmiş klasörü kaydedilen bilgilerden geri kurar. */
        fun restore(treeUri: Uri, dirId: String) = DataFolder(treeUri, dirId)

        private class Child(val id: String, val name: String, val isDir: Boolean)

        private fun children(context: Context, treeUri: Uri, parentId: String): List<Child> {
            val uri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentId)
            val projection = arrayOf(Document.COLUMN_DOCUMENT_ID, Document.COLUMN_DISPLAY_NAME, Document.COLUMN_MIME_TYPE)
            return context.contentResolver.query(uri, projection, null, null, null)?.use { c ->
                buildList {
                    while (c.moveToNext()) add(Child(c.getString(0), c.getString(1), c.getString(2) == Document.MIME_TYPE_DIR))
                }
            }.orEmpty()
        }

        private fun displayName(context: Context, treeUri: Uri, docId: String): String? {
            val uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
            return context.contentResolver.query(uri, arrayOf(Document.COLUMN_DISPLAY_NAME), null, null, null)
                ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
        }

        /** "primary:Documents/GaleriReels" → "Telefon hafızası › Documents › GaleriReels" */
        private fun readablePath(docId: String): String {
            val volume = docId.substringBefore(':', "")
            val path = docId.substringAfter(':')
            val root = if (volume == "primary") "Telefon hafızası" else "SD kart"
            return (listOf(root) + path.split('/').filter { it.isNotEmpty() }).joinToString(" › ")
        }

        private val README = """
            |Bu klasör Galeri Reels uygulamasının verilerini tutar:
            |hangi videoları beğendiğin ve her videoyu kaç kez izlediğin (galerireels-veri.json).
            |
            |Uygulamayı silip yeniden kursan bile bu bilgiler burada kalır. Yeniden kurduğunda
            |uygulama klasör sorunca bu klasörü (Documents › GaleriReels) seçmen yeterli;
            |her şey geri gelir.
            |
            |Bu klasörü ve içindeki dosyaları silme, taşıma ya da adını değiştirme.
            |Yedeklemek istersen klasörü olduğu gibi kopyalayabilirsin.
            |""".trimMargin()
    }
}
