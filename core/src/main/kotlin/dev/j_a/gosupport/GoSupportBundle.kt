package dev.j_a.gosupport

import com.intellij.DynamicBundle
import org.jetbrains.annotations.PropertyKey

object GoSupportBundle {
    val bundle = DynamicBundle(javaClass, "messages.GoSupportBundle")
}

fun i18n(@PropertyKey(resourceBundle = "messages.GoSupportBundle") key: String, vararg params: Any): String {
    return GoSupportBundle.bundle.getMessage(key, *params)
}