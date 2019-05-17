package com.badoo.ribs.core.routing.backstack

import android.os.Parcelable
import com.badoo.ribs.core.routing.backstack.feature.BackStackFeature.State as BackStackFeatureState
import com.badoo.ribs.core.routing.backstack.ConfigurationCommand.SingleConfigurationCommand.Activate
import com.badoo.ribs.core.routing.backstack.ConfigurationCommand.SingleConfigurationCommand.Add
import com.badoo.ribs.core.routing.backstack.ConfigurationCommand.SingleConfigurationCommand.Deactivate
import com.badoo.ribs.core.routing.backstack.ConfigurationCommand.SingleConfigurationCommand.Remove
import com.badoo.ribs.core.routing.backstack.ConfigurationKey.Content
import com.badoo.ribs.core.routing.backstack.feature.BackStackFeature
import io.reactivex.Observable
import java.lang.Math.min

/**
 * Takes the state emissions from [BackStackFeature], and translates them to a stream of
 * [ConfigurationCommand]s.
 *
 * @see [ConfigurationCommandCreator.diff]
 */
internal fun <C : Parcelable> BackStackFeature<C>.commands(): Observable<ConfigurationCommand<C>> =
    Observable.wrap(this)
        .buffer(2, 1)
        .map { ConfigurationCommandCreator.diff(it[0], it[1]) }
        .flatMapIterable { items -> items }

internal object ConfigurationCommandCreator {

    /**
     * Calculates diff between two states of [BackStackFeature] (where each state contains the current list
     * of [C] configurations representing the back stack), and translates the diff to
     * a list of [ConfigurationCommand]s that represent getting from the old state to the new one.
     */
    fun <C : Parcelable> diff(
        oldState: BackStackFeatureState<C>,
        newState: BackStackFeatureState<C>
    ): List<ConfigurationCommand<C>> {

        if (newState == oldState) {
            return emptyList()
        }

        val indexOfLastCommonElement = findIndexOfLastCommonElement(oldState.backStack, newState.backStack)
        val commands = mutableListOf<ConfigurationCommand<C>>()
        commands += oldState.backStack.deactivateIfNeeded()
        commands += oldState.backStack.removeUntil(indexOfLastCommonElement)
        commands += newState.backStack.addFrom(indexOfLastCommonElement)
        commands += newState.backStack.activateIfNeeded()

        return commands
    }

    private fun findIndexOfLastCommonElement(oldStack: List<*>, newStack: List<*>): Int {
        var idx = -1

        for (i in 0..min(oldStack.lastIndex, newStack.lastIndex)) {
            if (newStack.elementAt(i) != oldStack.elementAt(i)) {
                return idx
            }

            idx = i
        }

        return idx
    }

    private fun <C : Parcelable> List<C>.deactivateIfNeeded(): List<ConfigurationCommand<C>> =
        when {
            lastIndex > -1 -> listOf(Deactivate(Content(lastIndex)))
            else -> emptyList()
        }

    private fun <C : Parcelable> List<C>.removeUntil(targetIdxExclusive: Int): List<ConfigurationCommand<C>> {
        val offset = targetIdxExclusive + 1
        return subList(offset, size)
            .mapIndexed { index, _ -> Remove<C>(Content(offset + index)) }
            .asReversed()
    }

    private fun <C : Parcelable> List<C>.addFrom(targetIdxExclusive: Int): List<ConfigurationCommand<C>> {
        val offset = targetIdxExclusive + 1
        return subList(offset, size)
            .mapIndexed { index, configuration -> Add(Content(offset + index), configuration) }
    }

    private fun <C : Parcelable> List<C>.activateIfNeeded(): List<ConfigurationCommand<C>> =
        when {
            lastIndex > -1 -> listOf(Activate(Content(lastIndex)))
            else -> emptyList()
        }
}
