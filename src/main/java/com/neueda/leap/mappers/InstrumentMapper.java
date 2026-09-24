package com.neueda.leap.mappers;

import com.neueda.leap.model.Instrument;
import org.apache.ibatis.annotations.*;

/**
 * MyBatis mapper for Instrument persistence.
 */
@Mapper
public interface InstrumentMapper {
    /**
     * Finds an instrument by symbol.
     *
     * @param symbol the instrument symbol
     * @return the Instrument if found, null otherwise
     */
    @Select("SELECT symbol, name, asset_class, currency, tradable " +
            "FROM instruments WHERE symbol = #{symbol}")
    Instrument findBySymbol(String symbol);
}
