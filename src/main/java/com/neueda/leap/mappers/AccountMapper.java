package com.neueda.leap.mappers;

import com.neueda.leap.model.Account;
import org.apache.ibatis.annotations.*;

/**
 * MyBatis mapper for Account persistence.
 */
@Mapper
public interface AccountMapper {
    /**
     * Finds an account by ID.
     *
     * @param id the account ID
     * @return the Account if found, null otherwise
     */
    @Select("SELECT id, account_id, holder_name, cash_balance, status, version, last_updated " +
            "FROM accounts WHERE id = #{id}")
    Account findById(Long id);

    /**
     * Saves or updates an account.
     *
     * @param account the account to persist
     */
    @Insert("INSERT INTO accounts (account_id, holder_name, cash_balance, status, version, last_updated) " +
            "VALUES (#{accountId}, #{holderName}, #{cashBalance}, #{status}, #{version}, #{lastUpdated}) " +
            "ON CONFLICT (id) DO UPDATE SET " +
            "cash_balance = #{cashBalance}, status = #{status}, version = #{version}, last_updated = #{lastUpdated}")
    void save(Account account);
}
