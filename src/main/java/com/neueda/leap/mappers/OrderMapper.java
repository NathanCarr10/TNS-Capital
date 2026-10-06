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
     * Checks whether an order with the given idempotency key already exists.
     * Returns a boolean rather than mapping an Order, because Order.setStatus
     * rejects the null-to-status transition MyBatis would perform.
     *
     * @param idempotencyKey the normalized idempotency key
     * @return true if an order with this key exists
     */
    @Select("SELECT EXISTS (SELECT 1 FROM orders WHERE idempotency_key = #{idempotencyKey})")
    boolean existsByIdempotencyKey(String idempotencyKey);

    /**
     * Finds an order by ID.
     *
     * @param id the order ID
     * @return the Order if found, null otherwise
     */
    @Select("SELECT id, account_id, symbol, side, quantity, price, status, idempotency_key, created_on " +
            "FROM orders WHERE id = #{id, javaType=java.util.UUID, jdbcType=VARCHAR}")
    Order findById(@Param("id") UUID id);

    /**
     * Saves or updates an order.
     *
     * @param order the order to persist
     */
    @Insert("INSERT INTO orders (id, account_id, symbol, side, quantity, price, status, idempotency_key, created_on) " +
            "VALUES (#{id, javaType=java.util.UUID, jdbcType=VARCHAR}, #{accountId}, #{symbol}, #{side}, #{quantity}, #{price}, #{status}, #{idempotencyKey}, #{createdOn}) " +
            "ON CONFLICT (idempotency_key) DO UPDATE SET " +
            "status = #{status}")
    void save(Order order);

    /**
     * Updates order status.
     *
     * @param id the order ID
     * @param status the new status
     */
    @Update("UPDATE orders SET status = #{status} WHERE id = #{id, javaType=java.util.UUID, jdbcType=VARCHAR}")
    void updateStatus(@Param("id") UUID id, @Param("status") String status);
}
