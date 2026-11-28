package me.veilsu.manager.ui.screen.kpm

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateMapOf

/**
 * A best-effort visual model of a KPM `ctl0` command.
 *
 * The host only publishes `name`, `usage` and `help` (see `.kpm.commands`), so the
 * usage string is parsed into a small template: placeholders become text/number
 * fields, `a|b|c` becomes a choice, `[--flag]` becomes a switch and `[<value>]`
 * becomes an optional field. Anything the parser does not understand stays as a
 * literal token, and the raw argument editor is always available as a fallback.
 */
internal enum class KpmControlKind {
    LITERAL,
    TEXT,
    NUMBER,
    CHOICE,
    TOGGLE,
    FLAG_VALUE,
}

@Immutable
internal data class KpmControlField(
    val kind: KpmControlKind,
    val label: String = "",
    val literal: String = "",
    val optional: Boolean = false,
    val numeric: Boolean = false,
    val range: IntRange? = null,
    val options: List<String> = emptyList(),
    val flag: String = "",
    val valueLabel: String = "",
)

@Immutable
internal data class KpmFieldValue(
    val text: String = "",
    val choice: String = "",
    val checked: Boolean = false,
)

@Immutable
internal data class KpmControlTemplate(
    val commandName: String,
    val fields: List<KpmControlField>,
) {
    fun build(values: Map<Int, KpmFieldValue>): String? {
        val tokens = mutableListOf(commandName)
        fields.forEachIndexed { index, field ->
            val value = values[index] ?: KpmFieldValue()
            when (field.kind) {
                KpmControlKind.LITERAL -> tokens += field.literal
                KpmControlKind.TEXT -> {
                    if (field.optional && !value.checked) return@forEachIndexed
                    val text = value.text.trim()
                    if (text.isEmpty()) return null
                    tokens += text
                }
                KpmControlKind.NUMBER -> {
                    if (field.optional && !value.checked) return@forEachIndexed
                    val text = value.text.trim()
                    val number = text.toIntOrNull() ?: return null
                    if (field.range != null && number !in field.range) return null
                    tokens += text
                }
                KpmControlKind.CHOICE -> {
                    if (field.optional && !value.checked) return@forEachIndexed
                    val choice = value.choice.ifBlank { field.options.firstOrNull().orEmpty() }
                    if (choice.isEmpty()) return null
                    tokens += choice
                }
                KpmControlKind.TOGGLE -> if (value.checked) tokens += field.flag
                KpmControlKind.FLAG_VALUE -> {
                    if (field.optional && !value.checked) return@forEachIndexed
                    tokens += field.flag
                    when {
                        field.options.isNotEmpty() -> {
                            val choice = value.choice.ifBlank { field.options.firstOrNull().orEmpty() }
                            if (choice.isEmpty()) return null
                            tokens += choice
                        }
                        field.numeric -> {
                            val text = value.text.trim()
                            val number = text.toIntOrNull() ?: return null
                            if (field.range != null && number !in field.range) return null
                            tokens += text
                        }
                        else -> {
                            val text = value.text.trim()
                            if (text.isEmpty()) return null
                            tokens += text
                        }
                    }
                }
            }
        }
        return tokens.joinToString(" ")
    }
}

/**
 * Editor state for one [KpmControlTemplate]. Values are keyed by field index so
 * the UI can render fields in declaration order without carrying ids around.
 */
@Stable
internal class KpmControlFormState(
    val template: KpmControlTemplate,
    initialArgs: String,
) {
    val values: Map<Int, KpmFieldValue>
        field = mutableStateMapOf<Int, KpmFieldValue>()

    init {
        load(initialArgs)
    }

    fun value(index: Int): KpmFieldValue = values[index] ?: KpmFieldValue()

    fun updateText(index: Int, text: String) {
        values[index] = value(index).copy(text = text)
    }

    fun updateChoice(index: Int, choice: String) {
        values[index] = value(index).copy(choice = choice)
    }

    fun updateChecked(index: Int, checked: Boolean) {
        values[index] = value(index).copy(checked = checked)
    }

    fun compose(): String? = template.build(values)

    /** Reset to defaults and apply the tokens from [args] where they match. */
    fun load(args: String) {
        reset()
        val tokens = argumentTokens(args).toMutableList()
        if (tokens.firstOrNull() == template.commandName) tokens.removeAt(0)

        // Flags can appear anywhere; pull them out before matching ordered values.
        template.fields.forEachIndexed { index, field ->
            when (field.kind) {
                KpmControlKind.TOGGLE -> {
                    val position = tokens.indexOf(field.flag)
                    if (position >= 0) {
                        values[index] = value(index).copy(checked = true)
                        tokens.removeAt(position)
                    }
                }
                KpmControlKind.FLAG_VALUE -> {
                    val position = tokens.indexOf(field.flag)
                    if (position < 0) return@forEachIndexed
                    val next = tokens.getOrNull(position + 1)
                    val hasValue = next != null &&
                        (next.toIntOrNull() != null || !next.startsWith("-"))
                    tokens.removeAt(position)
                    if (hasValue) {
                        tokens.removeAt(position)
                        values[index] = value(index).copy(
                            checked = true,
                            text = next,
                            choice = next,
                        )
                    } else {
                        values[index] = value(index).copy(checked = true)
                    }
                }
                else -> Unit
            }
        }

        var cursor = 0
        template.fields.forEachIndexed { index, field ->
            when (field.kind) {
                KpmControlKind.LITERAL -> {
                    if (tokens.getOrNull(cursor) == field.literal) cursor++
                }
                KpmControlKind.TEXT, KpmControlKind.NUMBER -> {
                    val token = tokens.getOrNull(cursor) ?: return@forEachIndexed
                    if (token.startsWith("-")) return@forEachIndexed
                    cursor++
                    values[index] = value(index).copy(checked = true, text = token)
                }
                KpmControlKind.CHOICE -> {
                    val token = tokens.getOrNull(cursor) ?: return@forEachIndexed
                    if (token.startsWith("-")) return@forEachIndexed
                    cursor++
                    values[index] = value(index).copy(checked = true, choice = token)
                }
                else -> Unit
            }
        }
    }

    private fun reset() {
        values.clear()
        template.fields.forEachIndexed { index, field ->
            values[index] = when (field.kind) {
                KpmControlKind.CHOICE -> KpmFieldValue(
                    choice = field.options.firstOrNull().orEmpty(),
                    checked = !field.optional,
                )
                KpmControlKind.NUMBER -> KpmFieldValue(
                    text = field.range?.first?.toString().orEmpty(),
                    checked = !field.optional,
                )
                KpmControlKind.FLAG_VALUE -> KpmFieldValue(
                    text = field.range?.first?.toString().orEmpty(),
                    choice = field.options.firstOrNull().orEmpty(),
                    checked = !field.optional,
                )
                else -> KpmFieldValue(checked = !field.optional)
            }
        }
    }
}

private val usageTokenRegex =
    Regex("\\[[^]]*]|<[^>]*>|\"[^\"]*\"|'[^']*'|\\S+")
private val argumentTokenRegex = Regex("\"[^\"]*\"|'[^']*'|\\S+")

private val numericRangeRegex = Regex("^(-?\\d+)\\s*(?:\\.\\.|-|~)\\s*(-?\\d+)$")
private val numericWords = setOf(
    "num", "count", "size", "len", "length", "level", "port", "pid", "uid", "gid",
    "ms", "timeout", "delay", "second", "seconds", "minute", "minutes", "byte",
    "bytes", "index", "id", "slot", "cpu", "step", "retry", "retries",
)

internal fun parseKpmControlTemplate(command: KpmCommand): KpmControlTemplate {
    val usage = command.usage.ifBlank { command.name }.trim()
    val tokens = usageTokenRegex.findAll(usage).map { it.value }.toList()
    val first = tokens.firstOrNull()
    val firstIsCommand = first != null &&
        !first.startsWith("<") && !first.startsWith("[") && !first.startsWith("-")
    val commandName = if (firstIsCommand) unquote(first) else command.name
    val remaining = if (firstIsCommand) tokens.drop(1) else tokens
    return KpmControlTemplate(
        commandName = commandName,
        fields = parseTemplateTokens(remaining, optionalContext = false),
    )
}

private fun parseTemplateTokens(
    tokens: List<String>,
    optionalContext: Boolean,
): List<KpmControlField> {
    val fields = mutableListOf<KpmControlField>()
    var index = 0
    while (index < tokens.size) {
        val raw = tokens[index]
        if (raw.startsWith("[") && raw.endsWith("]")) {
            val inner = raw.substring(1, raw.length - 1).trim()
            if (inner.isNotEmpty()) {
                val innerTokens = usageTokenRegex.findAll(inner).map { it.value }.toList()
                fields += parseTemplateTokens(innerTokens, optionalContext = true)
            }
            index++
            continue
        }
        if (raw.startsWith("-")) {
            val equals = raw.indexOf('=')
            if (equals > 0) {
                val valuePart = raw.substring(equals + 1)
                if (valuePart.startsWith("<") && valuePart.endsWith(">")) {
                    fields += flagValueField(
                        flag = raw.substring(0, equals),
                        valueToken = valuePart,
                        optional = optionalContext,
                    )
                } else {
                    // `--key=literal` is a fixed token; inside an optional group
                    // it becomes a switch that appends the whole token.
                    fields += if (optionalContext) {
                        KpmControlField(
                            kind = KpmControlKind.TOGGLE,
                            label = raw.trimStart('-'),
                            flag = raw,
                            optional = true,
                        )
                    } else {
                        KpmControlField(kind = KpmControlKind.LITERAL, literal = raw)
                    }
                }
                index++
                continue
            }
            val next = tokens.getOrNull(index + 1)
            val nextInner = next?.let(::unwrapOptional)
            if (nextInner != null && nextInner.startsWith("<") && nextInner.endsWith(">")) {
                fields += flagValueField(raw, nextInner, optionalContext)
                index += 2
                continue
            }
            fields += if (optionalContext) {
                KpmControlField(
                    kind = KpmControlKind.TOGGLE,
                    label = raw.trimStart('-'),
                    flag = raw,
                    optional = true,
                )
            } else {
                KpmControlField(kind = KpmControlKind.LITERAL, literal = raw)
            }
            index++
            continue
        }
        if (raw.startsWith("<") && raw.endsWith(">")) {
            fields += placeholderField(raw, optionalContext, flag = null)
            index++
            continue
        }
        val literal = unquote(raw)
        fields += if (optionalContext) {
            // A bare word inside `[ ... ]` is an optional literal such as
            // `[all]`; model it as a switch that appends the word.
            KpmControlField(
                kind = KpmControlKind.TOGGLE,
                label = literal,
                flag = literal,
                optional = true,
            )
        } else {
            KpmControlField(kind = KpmControlKind.LITERAL, literal = literal)
        }
        index++
    }
    return fields
}

private fun placeholderField(
    token: String,
    optional: Boolean,
    flag: String?,
): KpmControlField {
    val content = token.removeSurrounding("<", ">").trim()
    val options = choiceOptions(content)
    val (numeric, range) = numericInfo(content)
    if (flag != null) {
        return KpmControlField(
            kind = KpmControlKind.FLAG_VALUE,
            label = flag.trimStart('-'),
            flag = flag,
            optional = optional,
            numeric = numeric,
            range = range,
            options = options.orEmpty(),
            valueLabel = content,
        )
    }
    return when {
        options != null -> KpmControlField(
            kind = KpmControlKind.CHOICE,
            label = content.replace('|', '/'),
            optional = optional,
            options = options,
            valueLabel = content,
        )
        numeric -> KpmControlField(
            kind = KpmControlKind.NUMBER,
            label = content,
            optional = optional,
            numeric = true,
            range = range,
            valueLabel = content,
        )
        else -> KpmControlField(
            kind = KpmControlKind.TEXT,
            label = content,
            optional = optional,
            valueLabel = content,
        )
    }
}

private fun flagValueField(flag: String, valueToken: String, optional: Boolean): KpmControlField {
    val token = if (valueToken.startsWith("<") && valueToken.endsWith(">")) {
        valueToken
    } else {
        "<$valueToken>"
    }
    return placeholderField(token, optional, flag)
}

private fun choiceOptions(content: String): List<String>? {
    if (!content.contains('|')) return null
    val options = content.split('|').map { it.trim() }.filter { it.isNotEmpty() }
    return options.takeIf { it.size >= 2 }
}

private fun numericInfo(content: String): Pair<Boolean, IntRange?> {
    numericRangeRegex.matchEntire(content)?.let { match ->
        val start = match.groupValues[1].toIntOrNull()
        val end = match.groupValues[2].toIntOrNull()
        if (start != null && end != null && start <= end) return true to (start..end)
    }
    if (content.isNotEmpty() && content.toIntOrNull() != null) return true to null
    val numeric = content.lowercase()
        .split('-', '_', ' ')
        .any { it in numericWords }
    return numeric to null
}

private fun unwrapOptional(token: String): String {
    if (!token.startsWith("[") || !token.endsWith("]")) return token
    return token.substring(1, token.length - 1)
}

private fun argumentTokens(value: String): List<String> =
    argumentTokenRegex.findAll(value).map { unquote(it.value) }.toList()

/**
 * First token of an argument string, used to line the visual command picker up
 * with the command a module was saved with (`kpm list` publishes `args`).
 */
internal fun kpmFirstArgumentToken(value: String): String? =
    argumentTokens(value).firstOrNull()

private fun unquote(value: String): String {
    if (value.length >= 2) {
        val first = value.first()
        if ((first == '"' || first == '\'') && value.last() == first) {
            return value.substring(1, value.length - 1)
        }
    }
    return value
}
