package com.neueda.leap.repositories;

import com.neueda.leap.model.Account;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;

/**
 * Repository interface for Account persistence.
 * 
 * Extends JpaRepository for Spring Data JPA integration.
 * Provides CRUD operations and custom query methods.
 */
public interface AccountRepository extends JpaRepository<Account, Long> {
    /**
     * Finds an account by its unique account number string.
     *
     * @param accountNumber the account number to search for
     * @return Optional containing the account if found, empty otherwise
     */
    Optional<Account> findByAccountNumber(String accountNumber);

    /**
     * Finds the account owned by a user. The users table belongs to the auth
     * service, which links each registered user to the account it opens for them.
     *
     * @param username the user's username (the JWT "sub" claim)
     * @return the owned account's ID, empty if the user has none (e.g. admins)
     */
    @Query(value = "SELECT account_id FROM users WHERE username = :username AND account_id IS NOT NULL",
            nativeQuery = true)
    Optional<Long> findAccountIdByUsername(@Param("username") String username);
}
