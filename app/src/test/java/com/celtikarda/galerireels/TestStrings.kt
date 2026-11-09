package com.celtikarda.galerireels

import org.w3c.dom.Element
import java.io.File
import java.util.Locale
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Birim testlerinde Android kaynakları yüklenmez; bu kaynak metinleri doğrudan
 * `src/main/res/values(-tr)/strings.xml`'den okur (Gradle testleri `app/` klasöründe çalıştırır).
 */
object TestStrings {
    fun use(language: String) {
        val dir = if (language == "en") "values" else "values-$language"
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder()
            .parse(File("src/main/res/$dir/strings.xml"))
        val strings = HashMap<String, String>()
        val plurals = HashMap<String, Map<String, String>>()
        val root = doc.documentElement
        for (i in 0 until root.childNodes.length) {
            val node = root.childNodes.item(i) as? Element ?: continue
            when (node.tagName) {
                "string" -> strings[node.getAttribute("name")] = unescape(node.textContent)
                "plurals" -> {
                    val items = node.getElementsByTagName("item")
                    plurals[node.getAttribute("name")] = (0 until items.length).associate {
                        val item = items.item(it) as Element
                        item.getAttribute("quantity") to unescape(item.textContent)
                    }
                }
            }
        }
        val stringNames = idsToNames(R.string::class.java)
        val pluralNames = idsToNames(R.plurals::class.java)
        val loc = Locale.forLanguageTag(language)

        Strings.source = object : Strings.Source {
            override val locale: Locale = loc
            override fun get(id: Int, args: Array<out Any>): String {
                val text = strings.getValue(stringNames.getValue(id))
                return if (args.isEmpty()) text else String.format(loc, text, *args)
            }
            override fun plural(id: Int, count: Int, args: Array<out Any>): String {
                val forms = plurals.getValue(pluralNames.getValue(id))
                val text = (if (count == 1) forms["one"] else null) ?: forms.getValue("other")
                return String.format(loc, text, *args)
            }
        }
    }

    private fun idsToNames(cls: Class<*>): Map<Int, String> =
        cls.fields.associate { it.getInt(null) to it.name }

    private fun unescape(s: String) = s.replace("\\n", "\n").replace("\\'", "'").replace("\\\"", "\"")
}
