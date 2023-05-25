// ISSUE: KT-58940

// FILE: JavaIndex.java
public class JavaIndex {
    public String getIndexer() { return ""; }
}

// FILE: main.kt
abstract class KotlinIndex : JavaIndex() {
    fun indexer(x: Int): String = ""
}

class MyKotlinIndex : KotlinIndex() {
    val INDEXER = indexer(1)

    override fun getIndexer() = INDEXER
}

fun main() {
    MyKotlinIndex().getIndexer().length
}
