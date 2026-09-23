package com.neueda.leap.mappers;

import com.neueda.leap.model.Order;
import org.apache.ibatis.annotations.*;

/**
 * MyBatis mapper for Order persistence.
 */
@Mapper
public interface OrderMapper {
    /**
     * Finds an order by idempotency key.
     *
     * @param idempotencyKey the idempotency key
     * @return the Order if found, null otherwise
     */
    @Select("SELECT id, account_id, symbol, side, quantity, price, status, idempotency_key, created_on " +
            "FROM orders WHERE idempotency_key = #{idempotencyKey}")
    Order findByIdempotencyKey(String idempotencyKey);

    /**
     * Saves or updates an order.
     *
     * @param order the order to persist
     */
    @Insert("INSERT INTO orders (id, account_id, symbol, side, quantity, price, status, idempotency_key, created_on) " +
            "VALUES (#{id}, #{accountId}, #{symbol}, #{side}, #{quantity}, #{price}, #{status}, #{idempotencyKey}, #{createdOn}) " +
            "ON CONFLICT (idempotency_key) DO UPDATE SET " +
            "status = #{status}")
    void save(Order order);
}
