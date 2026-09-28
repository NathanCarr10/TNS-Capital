package com.neueda.leap.mappers;

import com.neueda.leap.model.Position;
import org.apache.ibatis.annotations.*;

/**
 * MyBatis mapper for Position persistence.
 */
@Mapper
public interface PositionMapper {
    /**
     * Finds a position by account ID and symbol.
     *
     * @param accountId the account ID
     * @param symbol the instrument symbol
     * @return the Position if found, null otherwise
     */
    @Select("SELECT account_id, symbol, quantity, average_cost " +
            "FROM positions WHERE account_id = #{accountId} AND symbol = #{symbol}")
    Position findPosition(Long accountId, String symbol);

    /**
     * Saves or updates a position.
     *
     * @param position the position to persist
     */
    @Insert("INSERT INTO positions (account_id, symbol, quantity, average_cost) " +
            "VALUES (#{accountId}, #{symbol}, #{quantity}, #{averageCost}) " +
            "ON CONFLICT (account_id, symbol) DO UPDATE SET " +
            "quantity = #{quantity}, average_cost = #{averageCost}")
    void save(Position position);

    /**
     * Deletes a position.
     *
     * @param accountId the account ID
     * @param symbol the instrument symbol
     */
    @Delete("DELETE FROM positions WHERE account_id = #{accountId} AND symbol = #{symbol}")
    void delete(Long accountId, String symbol);
}
