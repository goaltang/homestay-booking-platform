package com.homestay3.homestaybackend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.homestay3.homestaybackend.entity.Homestay;
import com.homestay3.homestaybackend.entity.Order;
import com.homestay3.homestaybackend.entity.OrderPriceSnapshot;
import com.homestay3.homestaybackend.repository.HomestayRepository;
import com.homestay3.homestaybackend.repository.OrderPriceSnapshotRepository;
import com.homestay3.homestaybackend.service.impl.PricingServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.redisson.api.RedissonClient;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.util.AopTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.UnexpectedRollbackException;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.interceptor.NameMatchTransactionAttributeSource;
import org.springframework.transaction.interceptor.RuleBasedTransactionAttribute;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringJUnitConfig(RefundPolicyTransactionTest.Config.class)
@ActiveProfiles("test")
class RefundPolicyTransactionTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-10T06:00:00Z"),
            ZoneId.of("Asia/Shanghai"));

    @Autowired
    private RefundPolicyCalculator calculator;
    @Autowired
    private PricingService pricingService;
    @Autowired
    private OrderPriceSnapshotRepository snapshotRepository;
    @Autowired
    private PlatformTransactionManager transactionManager;
    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        reset(snapshotRepository);
        jdbc.execute("CREATE TABLE IF NOT EXISTS refund_transaction_probe (probe_key VARCHAR(36) PRIMARY KEY)");
        assertThat(AopUtils.isAopProxy(pricingService)).isTrue();
        assertThat(AopUtils.getTargetClass(pricingService)).isEqualTo(PricingServiceImpl.class);
    }

    @Test
    void snapshotFailureFallsBackAndOuterWriteStillCommits() {
        when(snapshotRepository.findByOrderId(10L))
                .thenThrow(new DataAccessResourceFailureException("snapshot unavailable"));
        String probe = UUID.randomUUID().toString();
        AtomicReference<RefundPolicyCalculator.Quote> observed = new AtomicReference<>();

        RefundPolicyCalculator.Quote quote = writeAndCalculate(calculator, probe, observed);

        assertThat(quote.amount()).isEqualByComparingTo("100");
        assertThat(quote.description()).contains("全额退款 ¥100");
        assertThat(committedProbeCount(probe)).isEqualTo(1);
        verify(snapshotRepository).findByOrderId(10L);
    }

    @Test
    void requiredSnapshotReadWouldRollBackOuterWriteEvenAfterFallback() {
        when(snapshotRepository.findByOrderId(10L))
                .thenThrow(new DataAccessResourceFailureException("snapshot unavailable"));
        String probe = UUID.randomUUID().toString();
        AtomicReference<RefundPolicyCalculator.Quote> observed = new AtomicReference<>();
        RefundPolicyCalculator requiredCalculator = new RefundPolicyCalculator(requiredPricingService(), CLOCK);

        assertThatThrownBy(() -> writeAndCalculate(requiredCalculator, probe, observed))
                .isInstanceOf(UnexpectedRollbackException.class);

        // 计算器已成功降级返回金额，但 REQUIRED 内层失败仍使外层提交失败。
        assertThat(observed.get()).isNotNull();
        assertThat(observed.get().amount()).isEqualByComparingTo("100");
        assertThat(committedProbeCount(probe)).isZero();
    }

    @Test
    void successfulSnapshotReadUsesPayableAmountAndOuterWriteCommits() {
        OrderPriceSnapshot snapshot = OrderPriceSnapshot.builder()
                .orderId(10L)
                .dailyPriceJson("[]")
                .payableAmount(new BigDecimal("62.40"))
                .build();
        when(snapshotRepository.findByOrderId(10L)).thenReturn(Optional.of(snapshot));
        String probe = UUID.randomUUID().toString();

        RefundPolicyCalculator.Quote quote = writeAndCalculate(calculator, probe, new AtomicReference<>());

        assertThat(quote.amount()).isEqualByComparingTo("62.40");
        assertThat(quote.description()).contains("全额退款 ¥62.40");
        assertThat(committedProbeCount(probe)).isEqualTo(1);
    }

    private RefundPolicyCalculator.Quote writeAndCalculate(RefundPolicyCalculator selectedCalculator, String probe,
                                                           AtomicReference<RefundPolicyCalculator.Quote> observed) {
        return new TransactionTemplate(transactionManager).execute(status -> {
            jdbc.update("INSERT INTO refund_transaction_probe (probe_key) VALUES (?)", probe);
            Homestay homestay = new Homestay();
            homestay.setCancelPolicyType(2);
            Order order = Order.builder()
                    .id(10L)
                    .homestay(homestay)
                    .checkInDate(LocalDate.of(2026, 10, 12))
                    .nights(1)
                    .totalAmount(new BigDecimal("100"))
                    .build();
            RefundPolicyCalculator.Quote quote = selectedCalculator.calculate(order);
            observed.set(quote);
            return quote;
        });
    }

    private int committedProbeCount(String probe) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM refund_transaction_probe WHERE probe_key = ?",
                Integer.class, probe);
    }

    private PricingService requiredPricingService() {
        // 使用同一个真实实现目标，仅把事务传播换成旧 REQUIRED，作为回归负向对照。
        PricingServiceImpl target = AopTestUtils.getTargetObject(pricingService);
        RuleBasedTransactionAttribute required = new RuleBasedTransactionAttribute();
        required.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRED);
        required.setReadOnly(true);
        NameMatchTransactionAttributeSource attributes = new NameMatchTransactionAttributeSource();
        attributes.addTransactionalMethod("getPriceSnapshot", required);
        TransactionInterceptor interceptor = new TransactionInterceptor();
        interceptor.setTransactionManager(transactionManager);
        interceptor.setTransactionAttributeSource(attributes);
        interceptor.afterPropertiesSet();
        ProxyFactory proxy = new ProxyFactory(target);
        proxy.setProxyTargetClass(true);
        proxy.addAdvice(interceptor);
        return (PricingService) proxy.getProxy();
    }

    @TestConfiguration(proxyBeanMethods = false)
    @EnableTransactionManagement(proxyTargetClass = true)
    static class Config {

        @Bean
        DataSource dataSource() {
            DriverManagerDataSource dataSource = new DriverManagerDataSource();
            dataSource.setDriverClassName("org.h2.Driver");
            dataSource.setUrl("jdbc:h2:mem:refund_policy_transaction_test;DB_CLOSE_DELAY=-1");
            dataSource.setUsername("sa");
            dataSource.setPassword("");
            return dataSource;
        }

        @Bean
        PlatformTransactionManager transactionManager(DataSource dataSource) {
            return new DataSourceTransactionManager(dataSource);
        }

        @Bean
        JdbcTemplate jdbcTemplate(DataSource dataSource) {
            return new JdbcTemplate(dataSource);
        }

        @Bean
        OrderPriceSnapshotRepository snapshotRepository() {
            return mock(OrderPriceSnapshotRepository.class);
        }

        @Bean
        PricingService pricingService(OrderPriceSnapshotRepository snapshotRepository,
                                      ObjectProvider<SystemConfigService> systemConfigServiceProvider) {
            return new PricingServiceImpl(mock(HomestayRepository.class), snapshotRepository,
                    mock(PromotionMatchService.class), mock(CouponService.class), systemConfigServiceProvider,
                    new ObjectMapper(), mock(PricingEngineService.class), mock(RedissonClient.class));
        }

        @Bean
        RefundPolicyCalculator refundPolicyCalculator(PricingService pricingService) {
            return new RefundPolicyCalculator(pricingService, CLOCK);
        }
    }
}
