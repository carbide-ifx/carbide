package ifx.host.loadtest

import ifx.service.IService
import io.github.serpro69.kfaker.Faker
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow

interface ILoadTestService : IService {
    fun loadTimeTables(): Flow<Timetable.Line>
    fun loadDepartures(): Flow<Departure>
    fun loadTimeTablesList(): List<Timetable.Line>
    fun loadDeparturesList(): List<Departure>
}
class LoadTestService : ILoadTestService {

    override fun loadTimeTables(): Flow<Timetable.Line> = timetables.asFlow()

    override fun loadDepartures(): Flow<Departure> = departures.asFlow()

    override fun loadTimeTablesList(): List<Timetable.Line> = timetables

    override fun loadDeparturesList(): List<Departure> = departures


    companion object {
        val f = Faker()
        val timetables = List(1000) { f.randomClass.randomClassInstance<Timetable.Line>() }
        val departures = List(30000) { f.randomClass.randomClassInstance<Departure>() }
    }
}


//                Timetable.Line(
//                    id = f.random.nextUUID(),
//                    name = f.name.neutralFirstName(),
//                    quayId = "quay-${f.name.neutralFirstName()}",
//                    operatorId = f.name.neutralFirstName(),
//                    enturId = f.random.nextUUID(),
//                    publicCode = f.idNumber.invalid(),
//                    siriEnabled = true,
//                    blocks = List(3) {blockId ->
//                        Timetable.Block(
//                            id = blockId.toString(),
//                            name = f.name.neutralFirstName(),
//                            vesselAssignments = List(5){vaId ->
//                                Timetable.VesselAssignment(
//                                    id = vaId.toString(),
//                                    name = f.name.neutralFirstName(),
//                                    vessel = f.idNumber.,
//                                    blockId = blockId.to(),
//                                    from = TODO(),
//                                    to = TODO()
//                                )
//                            },
//                            travelPatterns = List(5){
//                                Timetable.TravelPattern()
//                            }
//                        )
//                    },
//                )
