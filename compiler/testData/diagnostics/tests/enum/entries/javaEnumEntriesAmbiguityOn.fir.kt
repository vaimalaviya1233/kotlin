// !LANGUAGE: +EnumEntries
// WITH_STDLIB
// FIR_DUMP

// FILE: JEnumEntry.java

public enum JEnumEntry {
    entries;
}

// FILE: JEnumStaticField.java

public enum JEnumStaticField {
    ;

    public static final int entries = 0;
}

// FILE: JEnumField.java

public enum JEnumField {
    ;

    public final int entries = 0;
}

// FILE: test.kt

fun test() {
    JEnumEntry.entries
    JEnumStaticField.entries

    JEnumField::entries
}
