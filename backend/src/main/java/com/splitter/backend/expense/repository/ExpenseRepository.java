package com.splitter.backend.expense.repository;

import com.splitter.backend.expense.model.Expense;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ExpenseRepository extends JpaRepository<Expense, UUID> {

    List<Expense> findByGroupId(UUID groupId);

    List<Expense> findByGroupIdOrderByCreatedAtDesc(UUID groupId);
}