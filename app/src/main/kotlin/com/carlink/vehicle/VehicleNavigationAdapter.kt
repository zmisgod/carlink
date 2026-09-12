package com.carlink.vehicle

import android.os.Bundle
import com.carlink.navigation.NavigationState

object VehicleNavigationAdapter {
    fun maneuver(type: Int): NavigationInfo.Maneuver =
        when (type) {
            1, 13, 20, 22, 47, 49, 52 -> NavigationInfo.Maneuver.LEFT
            2, 14, 21, 23, 48, 50, 51, 53 -> NavigationInfo.Maneuver.RIGHT
            4, 26 -> NavigationInfo.Maneuver.U_TURN
            6, 7, 18, 19, in 28..46 -> NavigationInfo.Maneuver.ROUNDABOUT
            10, 12, 24, 25, 27 -> NavigationInfo.Maneuver.ARRIVED
            else -> NavigationInfo.Maneuver.STRAIGHT
        }

    fun convert(state: NavigationState): NavigationInfo =
        NavigationInfo(
            "carlink-route", "", state.roadName.orEmpty(), state.destinationName.orEmpty(),
            maneuver(state.maneuverType), state.remainDistance, state.distanceToDestination,
            ((state.timeToDestination.coerceAtLeast(0).toLong() + 59) / 60).toInt(),
            ((state.timeToDestination.coerceAtLeast(0).toLong() + 59) / 60).toInt(), -1, 0, 0, 0, state.status == 2, false, Double.NaN, Double.NaN,
            -1, state.roundaboutExit,
        ).withDetails(
            NavigationDetails(
                Bundle().apply {
                    putInt("remainingSeconds", state.timeToDestination.coerceAtLeast(0))
                },
            ),
        )
}
