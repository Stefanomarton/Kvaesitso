package de.mm20.launcher2.unitconverter

import android.content.Context
import de.mm20.launcher2.preferences.search.UnitConverterSettings
import de.mm20.launcher2.search.data.UnitConverter
import de.mm20.launcher2.unitconverter.converters.AreaConverter
import de.mm20.launcher2.unitconverter.converters.Converter
import de.mm20.launcher2.unitconverter.converters.DataConverter
import de.mm20.launcher2.unitconverter.converters.LengthConverter
import de.mm20.launcher2.unitconverter.converters.MassConverter
import de.mm20.launcher2.unitconverter.converters.TemperatureConverter
import de.mm20.launcher2.unitconverter.converters.TimeConverter
import de.mm20.launcher2.unitconverter.converters.VelocityConverter
import de.mm20.launcher2.unitconverter.converters.VolumeConverter
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import org.koin.core.component.KoinComponent

interface UnitConverterRepository {
    fun search(query: String): Flow<UnitConverter?>
    suspend fun getAvailableConverters(): List<Converter>
}

internal class UnitConverterRepositoryImpl(
    private val context: Context,
    private val settings: UnitConverterSettings,
) : UnitConverterRepository, KoinComponent {

    override fun search(query: String): Flow<UnitConverter?> {
        if (query.isBlank()) return flowOf(null)
        return settings.distinctUntilChanged().map {
            if (!it.enabled) null
            else queryUnitConverter(query)
        }
    }

    override suspend fun getAvailableConverters(): List<Converter> {
        return listOf(
            MassConverter(context),
            LengthConverter(context),
            DataConverter(context),
            TimeConverter(context),
            VelocityConverter(context),
            AreaConverter(context),
            TemperatureConverter(context),
            VolumeConverter(context),
        )
    }

    private suspend fun queryUnitConverter(
        query: String,
    ): UnitConverter? {
        val regex = Regex("""([+\-]?[\d+\-e,.]+|[^\d>\-]+)""")

        val matches = regex.findAll(query)

        var inputStr: String? = null
        var inputValue: Double? = null
        var inputUnit: String? = null
        var outputUnit: String? = null

        for ((i, match) in matches.withIndex()) {
            when (i) {
                0 -> {
                    inputStr = match.value.trim()
                    inputValue = inputStr.toDoubleOrNull()
                        ?: inputStr.replace(',', '.').toDoubleOrNull()
                                ?: return null
                }
                1 -> inputUnit = match.value.trim()
                2 -> {
                    if (!match.value.contains("-") && !match.value.contains(">")) {
                        outputUnit = match.value.trim()
                    }
                }
                3 -> {
                    if (outputUnit == null) {
                        outputUnit = match.value.trim()
                        break
                    } else {
                        return null
                    }
                }
                else -> return null
            }
        }

        if (inputValue == null || inputUnit == null) {
            return null
        }

        val converters = getAvailableConverters()

        for (converter in converters) {
            if (!converter.isValidUnit(inputUnit)) continue
            if (outputUnit != null && !converter.isValidUnit(outputUnit)) continue
            return converter.convert(context, inputUnit, inputValue, outputUnit)
        }
        return null
    }
}