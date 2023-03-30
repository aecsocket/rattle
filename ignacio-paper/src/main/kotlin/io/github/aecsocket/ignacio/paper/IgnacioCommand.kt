package io.github.aecsocket.ignacio.paper

import io.github.aecsocket.alexandria.paper.BaseCommand
import io.github.aecsocket.glossa.messageProxy

internal class IgnacioCommand(
    private val ignacio: Ignacio
) : BaseCommand(ignacio, ignacio.glossa.messageProxy()) {
    init {

    }
}
