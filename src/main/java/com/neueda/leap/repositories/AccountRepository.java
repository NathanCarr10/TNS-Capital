package com.neueda.leap.repositories;

import com.neueda.leap.model.Account;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

/**
 * Repository interface for Account persistence.
 * 
 * Extends JpaRepository for Spring Data JPA integration.
 * Provides CRUD operations and custom query methods.
 */
@Repository
public interface AccountRepository extends JpaRepository<Account, Long> {
    /**
     * Finds an account by its unique account ID string.
     *
     * @param accountId the account ID to search for
     * @return Optional containing the account if found, empty otherwise
     */
    Optional<Account> findByAccountId(String accountId);
}
