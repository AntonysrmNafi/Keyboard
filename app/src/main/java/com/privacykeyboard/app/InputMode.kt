package com.privacykeyboard.app

enum class InputMode(val label: String) {
    ENGLISH("EN"),
    BANGLA_PHONETIC("বাং"),
    BANGLA_TRADITIONAL("বাং২")
}

fun InputMode.next(): InputMode = when (this) {
    InputMode.ENGLISH -> InputMode.BANGLA_PHONETIC
    InputMode.BANGLA_PHONETIC -> InputMode.BANGLA_TRADITIONAL
    InputMode.BANGLA_TRADITIONAL -> InputMode.ENGLISH
}
