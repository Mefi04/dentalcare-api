package com.dentalcare.api.modules.reports.repository;

import com.dentalcare.api.modules.appointments.model.AppointmentStatus;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Repository
public class JpaDashboardMetricsRepository implements DashboardMetricsRepository {

    private final EntityManager entityManager;

    public JpaDashboardMetricsRepository(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    public PatientMetricsSnapshot findPatientMetrics(Instant fromInclusive, Instant toExclusive) {
        Object[] row = entityManager.createQuery("""
                        SELECT COUNT(p), COALESCE(SUM(CASE
                            WHEN p.createdAt >= :fromInclusive AND p.createdAt < :toExclusive THEN 1
                            ELSE 0
                        END), 0)
                        FROM Patient p
                        """, Object[].class)
                .setParameter("fromInclusive", fromInclusive)
                .setParameter("toExclusive", toExclusive)
                .getSingleResult();
        return new PatientMetricsSnapshot(asLong(row[0]), asLong(row[1]));
    }

    @Override
    public Map<AppointmentStatus, Long> countAppointmentsByStatus(
            Instant fromInclusive, Instant toExclusive) {
        List<Object[]> rows = entityManager.createQuery("""
                        SELECT a.status, COUNT(a)
                        FROM Appointment a
                        WHERE a.scheduledAt >= :fromInclusive AND a.scheduledAt < :toExclusive
                        GROUP BY a.status
                        """, Object[].class)
                .setParameter("fromInclusive", fromInclusive)
                .setParameter("toExclusive", toExclusive)
                .getResultList();

        Map<AppointmentStatus, Long> result = new EnumMap<>(AppointmentStatus.class);
        rows.forEach(row -> result.put((AppointmentStatus) row[0], asLong(row[1])));
        return result;
    }

    @Override
    public BillingMetricsSnapshot findBillingMetrics(Instant fromInclusive, Instant toExclusive) {
        Object[] row = (Object[]) entityManager.createNativeQuery("""
                        SELECT
                            COALESCE((SELECT SUM(c.amount) FROM billing_charges c
                                WHERE c.created_at >= :fromInclusive AND c.created_at < :toExclusive), 0),
                            COALESCE((SELECT SUM(p.amount) FROM billing_payments p
                                WHERE p.created_at >= :fromInclusive AND p.created_at < :toExclusive), 0),
                            COALESCE((SELECT SUM(c.amount) FROM billing_charges c), 0),
                            COALESCE((SELECT SUM(p.amount) FROM billing_payments p), 0)
                        """)
                .setParameter("fromInclusive", fromInclusive)
                .setParameter("toExclusive", toExclusive)
                .getSingleResult();
        return new BillingMetricsSnapshot(
                asBigDecimal(row[0]), asBigDecimal(row[1]), asBigDecimal(row[2]), asBigDecimal(row[3]));
    }

    private long asLong(Object value) {
        return ((Number) value).longValue();
    }

    private BigDecimal asBigDecimal(Object value) {
        return value instanceof BigDecimal decimal ? decimal : new BigDecimal(value.toString());
    }
}
