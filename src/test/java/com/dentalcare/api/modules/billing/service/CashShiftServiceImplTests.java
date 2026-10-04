package com.dentalcare.api.modules.billing.service;

import com.dentalcare.api.exception.BadRequestException;
import com.dentalcare.api.exception.ConflictException;
import com.dentalcare.api.exception.ResourceNotFoundException;
import com.dentalcare.api.modules.billing.dto.request.CloseCashShiftRequest;
import com.dentalcare.api.modules.billing.dto.request.CreateCashMovementRequest;
import com.dentalcare.api.modules.billing.dto.request.OpenCashShiftRequest;
import com.dentalcare.api.modules.billing.dto.response.CashMovementResponse;
import com.dentalcare.api.modules.billing.dto.response.CashShiftResponse;
import com.dentalcare.api.modules.billing.mapper.CashShiftMapper;
import com.dentalcare.api.modules.billing.model.CashMovementType;
import com.dentalcare.api.modules.billing.model.CashShift;
import com.dentalcare.api.modules.billing.model.CashShiftStatus;
import com.dentalcare.api.modules.billing.repository.CashMovementRepository;
import com.dentalcare.api.modules.billing.repository.CashShiftRepository;
import com.dentalcare.api.modules.billing.repository.PaymentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CashShiftServiceImplTests {

    private static final Instant NOW = Instant.parse("2026-10-04T15:00:00Z");

    @Mock
    private CashShiftRepository cashShiftRepository;

    @Mock
    private CashMovementRepository cashMovementRepository;

    @Mock
    private PaymentRepository paymentRepository;

    private final UUID actorId = UUID.randomUUID();

    private CashShiftServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new CashShiftServiceImpl(cashShiftRepository, cashMovementRepository, paymentRepository,
                new CashShiftMapper(), Clock.fixed(NOW, ZoneOffset.UTC));
        lenient().when(cashShiftRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(cashMovementRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(paymentRepository.sumCashAmountByCashShiftId(any())).thenReturn(new BigDecimal("0.00"));
        lenient().when(cashMovementRepository.sumIncomeByCashShiftId(any())).thenReturn(new BigDecimal("0.00"));
        lenient().when(cashMovementRepository.sumExpenseByCashShiftId(any())).thenReturn(new BigDecimal("0.00"));
    }

    @Test
    void opensShiftForTheActor() {
        when(cashShiftRepository.findOpenByUserId(actorId)).thenReturn(Optional.empty());

        CashShiftResponse response = service.open(actorId, new OpenCashShiftRequest(new BigDecimal("100"), "  apertura  "));

        assertThat(response.userId()).isEqualTo(actorId);
        assertThat(response.status()).isEqualTo(CashShiftStatus.OPEN);
        assertThat(response.openingAmount()).isEqualByComparingTo("100.00");
        assertThat(response.expectedAmount()).isEqualByComparingTo("100.00");
        assertThat(response.openedAt()).isEqualTo(NOW);
        assertThat(response.openingNotes()).isEqualTo("apertura");
        assertThat(response.closedAt()).isNull();
        assertThat(response.countedAmount()).isNull();
        assertThat(response.difference()).isNull();
    }

    @Test
    void duplicateOpenIsConflict() {
        when(cashShiftRepository.findOpenByUserId(actorId)).thenReturn(Optional.of(openShift(actorId, "10.00")));

        assertThatThrownBy(() -> service.open(actorId, new OpenCashShiftRequest(new BigDecimal("20.00"), null)))
                .isInstanceOf(ConflictException.class)
                .hasMessage("A cash shift is already open");

        verify(cashShiftRepository, never()).saveAndFlush(any());
    }

    @Test
    void uniqueIndexViolationOnOpenIsConflict() {
        when(cashShiftRepository.findOpenByUserId(actorId)).thenReturn(Optional.empty());
        when(cashShiftRepository.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException(
                "could not execute statement",
                new RuntimeException("duplicate key value violates unique constraint \"uq_billing_cash_shifts_open_per_user\"")));

        assertThatThrownBy(() -> service.open(actorId, new OpenCashShiftRequest(new BigDecimal("20.00"), null)))
                .isInstanceOf(ConflictException.class)
                .hasMessage("A cash shift is already open");
    }

    @Test
    void recordsIncomeAndExpenseOnTheOpenShift() {
        CashShift shift = openShift(actorId, "50.00");
        when(cashShiftRepository.findByIdAndUserIdForUpdate(shift.getId(), actorId)).thenReturn(Optional.of(shift));

        CashMovementResponse income = service.addMovement(actorId, shift.getId(),
                new CreateCashMovementRequest(CashMovementType.INCOME, new BigDecimal("12.5"), "  Venta de agua  "));
        CashMovementResponse expense = service.addMovement(actorId, shift.getId(),
                new CreateCashMovementRequest(CashMovementType.EXPENSE, new BigDecimal("3.00"), "Insumos"));

        assertThat(income.type()).isEqualTo(CashMovementType.INCOME);
        assertThat(income.amount()).isEqualByComparingTo("12.50");
        assertThat(income.concept()).isEqualTo("Venta de agua");
        assertThat(income.cashShiftId()).isEqualTo(shift.getId());
        assertThat(income.createdByUserId()).isEqualTo(actorId);
        assertThat(income.createdAt()).isEqualTo(NOW);
        assertThat(expense.type()).isEqualTo(CashMovementType.EXPENSE);
        assertThat(expense.amount()).isEqualByComparingTo("3.00");
    }

    @Test
    void movementOnClosedShiftIsConflict() {
        CashShift shift = openShift(actorId, "50.00");
        shift.close(new BigDecimal("50.00"), new BigDecimal("50.00"), new BigDecimal("0.00"), null, NOW);
        when(cashShiftRepository.findByIdAndUserIdForUpdate(shift.getId(), actorId)).thenReturn(Optional.of(shift));

        assertThatThrownBy(() -> service.addMovement(actorId, shift.getId(),
                new CreateCashMovementRequest(CashMovementType.INCOME, new BigDecimal("1.00"), "Tarde")))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Cash shift is not open");

        verify(cashMovementRepository, never()).saveAndFlush(any());
    }

    @Test
    void nonPositiveMovementAmountIsBadRequest() {
        CashShift shift = openShift(actorId, "50.00");
        when(cashShiftRepository.findByIdAndUserIdForUpdate(shift.getId(), actorId)).thenReturn(Optional.of(shift));

        assertThatThrownBy(() -> service.addMovement(actorId, shift.getId(),
                new CreateCashMovementRequest(CashMovementType.EXPENSE, new BigDecimal("0.00"), "Nada")))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Amount must be greater than zero");
        assertThatThrownBy(() -> service.addMovement(actorId, shift.getId(),
                new CreateCashMovementRequest(CashMovementType.EXPENSE, new BigDecimal("-1.00"), "Nada")))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Amount must be greater than zero");
        assertThatThrownBy(() -> service.addMovement(actorId, shift.getId(),
                new CreateCashMovementRequest(CashMovementType.INCOME, new BigDecimal("1.00"), "   ")))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Concept is required");

        verify(cashMovementRepository, never()).saveAndFlush(any());
    }

    @Test
    void closeCalculatesExpectedAmountAndDifference() {
        CashShift shift = openShift(actorId, "100.00");
        when(cashShiftRepository.findByIdAndUserIdForUpdate(shift.getId(), actorId)).thenReturn(Optional.of(shift));
        when(paymentRepository.sumCashAmountByCashShiftId(shift.getId())).thenReturn(new BigDecimal("40.00"));
        when(cashMovementRepository.sumIncomeByCashShiftId(shift.getId())).thenReturn(new BigDecimal("15.00"));
        when(cashMovementRepository.sumExpenseByCashShiftId(shift.getId())).thenReturn(new BigDecimal("5.00"));

        CashShiftResponse response = service.close(actorId, shift.getId(),
                new CloseCashShiftRequest(new BigDecimal("140"), "  cuadre  "));

        assertThat(response.status()).isEqualTo(CashShiftStatus.CLOSED);
        assertThat(response.expectedAmount()).isEqualByComparingTo("150.00");
        assertThat(response.countedAmount()).isEqualByComparingTo("140.00");
        assertThat(response.difference()).isEqualByComparingTo("-10.00");
        assertThat(response.closedAt()).isEqualTo(NOW);
        assertThat(response.closingNotes()).isEqualTo("cuadre");
        assertThat(shift.getExpectedAmount()).isEqualByComparingTo("150.00");
    }

    @Test
    void closingTwiceIsConflict() {
        CashShift shift = openShift(actorId, "100.00");
        when(cashShiftRepository.findByIdAndUserIdForUpdate(shift.getId(), actorId)).thenReturn(Optional.of(shift));
        service.close(actorId, shift.getId(), new CloseCashShiftRequest(new BigDecimal("100.00"), null));

        assertThatThrownBy(() -> service.close(actorId, shift.getId(),
                new CloseCashShiftRequest(new BigDecimal("100.00"), null)))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Cash shift is already closed");
    }

    @Test
    void shiftOfAnotherUserIsNotFound() {
        UUID foreignShiftId = UUID.randomUUID();
        when(cashShiftRepository.findByIdAndUserIdForUpdate(foreignShiftId, actorId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.close(actorId, foreignShiftId,
                new CloseCashShiftRequest(new BigDecimal("10.00"), null)))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Cash shift not found");
        assertThatThrownBy(() -> service.addMovement(actorId, foreignShiftId,
                new CreateCashMovementRequest(CashMovementType.INCOME, new BigDecimal("1.00"), "Ajeno")))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Cash shift not found");

        verify(cashShiftRepository, never()).saveAndFlush(any());
        verify(cashMovementRepository, never()).saveAndFlush(any());
    }

    @Test
    void userWithoutReadAllOnlySeesOwnShifts() {
        UUID otherUserId = UUID.randomUUID();
        CashShift own = openShift(actorId, "20.00");
        CashShift foreign = openShift(otherUserId, "80.00");
        when(cashShiftRepository.findByUserId(eq(actorId), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(own)));
        when(cashShiftRepository.findById(foreign.getId())).thenReturn(Optional.of(foreign));

        assertThat(service.listShifts(actorId, false, PageRequest.of(0, 20)).getContent())
                .extracting(CashShiftResponse::userId)
                .containsExactly(actorId);
        assertThatThrownBy(() -> service.listMovements(actorId, false, foreign.getId(), PageRequest.of(0, 20)))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Cash shift not found");

        verify(cashShiftRepository, never()).findAll(any(Pageable.class));
        verify(cashMovementRepository, never()).findByCashShift_Id(any(), any());
    }

    @Test
    void listCapsPageSizeAtOneHundred() {
        when(cashShiftRepository.findByUserId(eq(actorId), any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));

        service.listShifts(actorId, false, PageRequest.of(0, 500));

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(cashShiftRepository).findByUserId(eq(actorId), pageable.capture());
        assertThat(pageable.getValue().getPageSize()).isEqualTo(100);
        assertThat(pageable.getValue().getSort().getOrderFor("openedAt").isDescending()).isTrue();
    }

    @Test
    void currentShiftNotFoundWhenActorHasNoOpenShift() {
        when(cashShiftRepository.findOpenByUserId(actorId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getCurrent(actorId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Cash shift not found");
    }

    @Test
    void missingOpenShiftForPaymentIsConflict() {
        when(cashShiftRepository.findOpenByUserIdForUpdate(actorId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.requireOpenShiftForUpdate(actorId))
                .isInstanceOf(ConflictException.class)
                .hasMessage("No open cash shift");
    }

    private CashShift openShift(UUID userId, String openingAmount) {
        return new CashShift(UUID.randomUUID(), userId, new BigDecimal(openingAmount), null, NOW.minusSeconds(30));
    }
}
