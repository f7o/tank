package io.marauder.tank

import com.datastax.driver.core.LocalDate
import com.datastax.driver.core.Session
import io.marauder.supercharged.models.GeoJSON
import io.marauder.supercharged.models.Value
import kotlinx.serialization.ImplicitReflectionSerializer
import org.slf4j.LoggerFactory

class Tyler(
        private val session: Session,
        dbTable: String = "features",
        addTimeStamp: Boolean = true,
        private val attrFields: List<String>) {

    private val attributes = attrFields.map { it.split(" ").first() }
    private val q = session.prepare("""
        INSERT INTO $dbTable (${if (attributes.isNotEmpty()) attributes.joinToString(", ", "", ",") else "" } geometry)
        VALUES (${ if (attributes.isNotEmpty()) attributes.map { if (addTimeStamp && it == "timestamp") "unixTimestampOf(now())" else ":$it" }.joinToString(", ", "", ", ") else "" } :geometry)
    """.trimIndent())


    @ImplicitReflectionSerializer
    fun import(input: GeoJSON) {

        log.info("#${input.features.size} features importing starts")
        input.features.forEachIndexed { i, f ->
            var endLog = marker.startLogDuration("prepare geometry")

            val bound = q.bind()
            attrFields.forEach { attr ->
                val (name, type) = attr.split(" ")


                if (name != "timestamp") {
                    when (type) {
                        "int" -> bound.setInt(name, (f.properties[name] as Value.IntValue).value.toInt())
                        "text" -> bound.setString(name, (f.properties[name] as Value.StringValue).value)
                        "date" -> {
                            val date = (f.properties["img_date"] as Value.StringValue).value.split('-')
                            bound.setDate(name, LocalDate.fromYearMonthDay(date[0].toInt(), date[1].toInt(), date[2].toInt()))
                        }
                        else -> TODO("type not supported yet")
                    }
                }
            }

            bound.setString("geometry", f.geometry.toWKT())


            endLog()
            endLog = marker.startLogDuration("store geometry to database")
            session.execute(bound)
            endLog()
            if (i % 1000 == 0) log.info("#$i features stored to DB")
        }

        log.info("#${input.features.size} features importing finished")
    }


    companion object {
        private val log = LoggerFactory.getLogger(Tyler::class.java)
        private val marker = Benchmark(log)
    }

}