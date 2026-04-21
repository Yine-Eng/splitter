package com.splitter.backend;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import com.splitter.backend.expense.model.Expense;
import com.splitter.backend.expense.model.ExpenseSplit;
import com.splitter.backend.expense.repository.ExpenseSplitRepository;
import com.splitter.backend.expense.service.ExpenseService;
import com.splitter.backend.group.model.Group;
import com.splitter.backend.group.model.GroupMember;
import com.splitter.backend.group.model.GroupRole;
import com.splitter.backend.group.repository.GroupMemberRepository;
import com.splitter.backend.group.repository.GroupRepository;
import com.splitter.backend.models.User;
import com.splitter.backend.repository.UserRepository;

@SpringBootTest
@ActiveProfiles("test")
public class ExpenseServiceRemainderRotationTest {

    @Autowired
    private ExpenseService expenseService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private GroupRepository groupRepository;

    @Autowired
    private GroupMemberRepository groupMemberRepository;

    @Autowired
    private ExpenseSplitRepository expenseSplitRepository;

    @Test
    void shouldRotateExtraCentAcrossExpenses() {

        User payer = userRepository.save(new User("payer_" + System.currentTimeMillis(), "encoded"));
        User member2 = userRepository.save(new User("member2_" + System.currentTimeMillis(), "encoded"));
        User member3 = userRepository.save(new User("member3_" + System.currentTimeMillis(), "encoded"));

        Group group = groupRepository.save(new Group("Rotation Group", payer.getId()));

        groupMemberRepository.save(new GroupMember(group.getId(), payer.getId(), GroupRole.ADMIN));
        groupMemberRepository.save(new GroupMember(group.getId(), member2.getId(), GroupRole.MEMBER));
        groupMemberRepository.save(new GroupMember(group.getId(), member3.getId(), GroupRole.MEMBER));

        List<Long> participants = List.of(payer.getId(), member2.getId(), member3.getId());

        // ---------- EXPENSE 1 ----------
        // 10.00 / 3 = 3.34, 3.33, 3.33 with remainder distributed from index 0
        Expense expense1 = expenseService.createExpense(
                group.getId(),
                "Lunch 1",
                new BigDecimal("10.00"),
                payer.getId(),
                participants
        );

        List<ExpenseSplit> expense1Splits = expenseSplitRepository.findByExpenseId(expense1.getId());

        ExpenseSplit split1Member2 = expense1Splits.stream()
                .filter(s -> s.getUserId().equals(member2.getId()))
                .findFirst()
                .orElseThrow();

        ExpenseSplit split1Member3 = expense1Splits.stream()
                .filter(s -> s.getUserId().equals(member3.getId()))
                .findFirst()
                .orElseThrow();

        // payer got the extra cent on expense 1, so non-payers stay at 3.33
        assertThat(split1Member2.getAmountOwed()).isEqualByComparingTo("3.33");
        assertThat(split1Member3.getAmountOwed()).isEqualByComparingTo("3.33");

        Group updatedGroupAfterExpense1 = groupRepository.findById(group.getId()).orElseThrow();
        assertThat(updatedGroupAfterExpense1.getRemainderStartIndex()).isEqualTo(1);

        // ---------- EXPENSE 2 ----------
        // next remainder starts at index 1, so member2 gets the extra cent
        Expense expense2 = expenseService.createExpense(
                group.getId(),
                "Lunch 2",
                new BigDecimal("10.00"),
                payer.getId(),
                participants
        );

        List<ExpenseSplit> expense2Splits = expenseSplitRepository.findByExpenseId(expense2.getId());

        ExpenseSplit split2Member2 = expense2Splits.stream()
                .filter(s -> s.getUserId().equals(member2.getId()))
                .findFirst()
                .orElseThrow();

        ExpenseSplit split2Member3 = expense2Splits.stream()
                .filter(s -> s.getUserId().equals(member3.getId()))
                .findFirst()
                .orElseThrow();

        assertThat(split2Member2.getAmountOwed()).isEqualByComparingTo("3.34");
        assertThat(split2Member3.getAmountOwed()).isEqualByComparingTo("3.33");

        Group updatedGroupAfterExpense2 = groupRepository.findById(group.getId()).orElseThrow();
        assertThat(updatedGroupAfterExpense2.getRemainderStartIndex()).isEqualTo(2);

        // ---------- EXPENSE 3 ----------
        // next remainder starts at index 2, so member3 gets the extra cent
        Expense expense3 = expenseService.createExpense(
                group.getId(),
                "Lunch 3",
                new BigDecimal("10.00"),
                payer.getId(),
                participants
        );

        List<ExpenseSplit> expense3Splits = expenseSplitRepository.findByExpenseId(expense3.getId());

        ExpenseSplit split3Member2 = expense3Splits.stream()
                .filter(s -> s.getUserId().equals(member2.getId()))
                .findFirst()
                .orElseThrow();

        ExpenseSplit split3Member3 = expense3Splits.stream()
                .filter(s -> s.getUserId().equals(member3.getId()))
                .findFirst()
                .orElseThrow();

        assertThat(split3Member2.getAmountOwed()).isEqualByComparingTo("3.33");
        assertThat(split3Member3.getAmountOwed()).isEqualByComparingTo("3.34");

        Group updatedGroupAfterExpense3 = groupRepository.findById(group.getId()).orElseThrow();
        assertThat(updatedGroupAfterExpense3.getRemainderStartIndex()).isEqualTo(0);

        // ---------- SANITY CHECK ----------
        // Each expense should still total exactly 10.00 across all participants
        assertThat(new BigDecimal("3.34").add(new BigDecimal("3.33")).add(new BigDecimal("3.33")))
                .isEqualByComparingTo("10.00");
    }
}