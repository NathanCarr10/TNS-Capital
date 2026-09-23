package com.neueda.leap.mappers;

import com.neueda.leap.model.Order;
import org.apache.ibatis.annotations.*;
import java.util.UUID;

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
     * Finds an order by ID.
     *
     * @param id the order ID
     * @return the Order if found, null otherwise
     */
    @Select("SELECT id, account_id, symbol, side, quantity, price, status, idempotency_key, created_on " +
            "FROM orders WHERE id = #{id}")
    Order findById(UUID id);

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

    /**
     * Updates order status.
     *
     * @param id the order ID
     * @param status the new status
     */
    @Update("UPDATE orders SET status = #{status} WHERE id = #{id}")
    void updateStatus(UUID id, String status);
}
