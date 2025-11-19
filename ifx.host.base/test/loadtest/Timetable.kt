package ifx.host.loadtest

import kotlinx.serialization.Serializable
import java.time.DayOfWeek

interface Timetable {
    @Serializable
    data class Line(
        val id: String,
        val name: String,
        val operatorId: String,
        val enturId: String,
        val publicCode: String,
        val siriEnabled: Boolean,
        val blocks: List<Block>,
    )

    @Serializable
    data class Block(
        val id: String,
        val name: String,
        val vesselAssignments: List<VesselAssignment>,
        val travelPatterns: List<TravelPattern>
    )

    @Serializable
    data class TravelPattern(
        val id: String,
        val name: String,
        val validities: List<TravelPatternValidity>,
        val departurePatterns: List<DeparturePattern>,
        val serviceJourneyId: String?
    )

    @Serializable
    data class TravelPatternValidity(
        val id: String,
        val name: String,
        val validFrom: String,
        val validTo: String,
        val isCancelled: Boolean,
        val repeatsOnDay: Set<DayOfWeek>
    )

    @Serializable
    data class DeparturePattern(
        val id: String,
        val departureQuay: Quay,
        val arrivalQuay: Quay,
        val departure: PassingTime,
        val arrival: PassingTime
    )

    @Serializable
    data class Quay(val id: String, val name: String)

    @Serializable
    data class PassingTime(val time: String, val dayOffset: Int)

    @Serializable
    data class VesselAssignment(
        val id: String,
        val name: String,
        val vessel: Int,
        val blockId: String,
        val from: String,
        val to: String
    )


}

@Serializable
data class Operator(val id: String, val name: String?)


@Serializable
data class Departure(
    val departureId: String,
    val lineId: String,
    val lineName: String,
    val operator: Operator,
    val tripReference: String,
    val departureQuayId: String,
    val departureQuayName: String,
    val departureTime: String,
    val arrivalQuayId: String,
    val arrivalQuayName: String,
    val arrivalTime: String,
    val vesselId: Int?,
    val block: String,
    val blockName: String,
    val travelPattern: String,
    val serviceJourneyId: String?, // Todo: Remove nullable when Bodø - Moskenes gets serviceJourneyIds
    val travelDate: String,
    val hasDeparted: Boolean
)
