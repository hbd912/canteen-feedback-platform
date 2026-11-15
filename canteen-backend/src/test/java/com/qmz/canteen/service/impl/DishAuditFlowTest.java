package com.qmz.canteen.service.impl;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisSqlSessionFactoryBuilder;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.qmz.canteen.common.BusinessException;
import com.qmz.canteen.common.ResultCode;
import com.qmz.canteen.dto.DishRequest;
import com.qmz.canteen.entity.Dish;
import com.qmz.canteen.entity.Notification;
import com.qmz.canteen.entity.Stall;
import com.qmz.canteen.mapper.CartItemMapper;
import com.qmz.canteen.mapper.DishMapper;
import com.qmz.canteen.mapper.DishReviewMapper;
import com.qmz.canteen.mapper.StallMapper;
import com.qmz.canteen.mapper.StatsMapper;
import com.qmz.canteen.service.NotificationService;
import com.qmz.canteen.vo.DishAuditVO;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.math.BigDecimal;
import java.sql.Statement;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class DishAuditFlowTest {

    private SqlSession session;
    private DishMapper dishMapper;
    private DishServiceImpl dishService;
    private DishAuditServiceImpl auditService;
    private NotificationService notificationService;

    @BeforeEach
    void setUp() throws Exception {
        MybatisConfiguration configuration = new MybatisConfiguration();
        configuration.setEnvironment(new Environment("test", new JdbcTransactionFactory(),
                new UnpooledDataSource("org.h2.Driver", "jdbc:h2:mem:" + UUID.randomUUID()
                        + ";MODE=MySQL", "sa", "")));
        configuration.addMapper(DishMapper.class);
        try (InputStream xml = Resources.getResourceAsStream("mapper/DishMapper.xml")) {
            assertNotNull(xml);
            new XMLMapperBuilder(xml, configuration, "mapper/DishMapper.xml",
                    configuration.getSqlFragments()).parse();
        }
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.H2));
        configuration.addInterceptor(interceptor);

        session = new MybatisSqlSessionFactoryBuilder().build(configuration).openSession();
        dishMapper = session.getMapper(DishMapper.class);
        notificationService = mock(NotificationService.class);

        try (Statement sql = session.getConnection().createStatement()) {
            sql.execute("CREATE TABLE dish ("
                    + "id BIGINT AUTO_INCREMENT PRIMARY KEY, "
                    + "stall_id BIGINT NOT NULL, name VARCHAR(100) NOT NULL, "
                    + "price DECIMAL(8,2) DEFAULT 0, category VARCHAR(50), image VARCHAR(255), "
                    + "description VARCHAR(500), featured INT DEFAULT 0, status INT DEFAULT 1, "
                    + "stock_qty INT, sold_out INT DEFAULT 0, "
                    + "create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP, "
                    + "update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP, "
                    + "deleted INT DEFAULT 0, "
                    + "audit_status VARCHAR(20) NOT NULL DEFAULT 'APPROVED', "
                    + "audit_remark VARCHAR(300), audit_by BIGINT, audit_time TIMESTAMP)");
            sql.execute("CREATE TABLE canteen_stall (id BIGINT PRIMARY KEY, name VARCHAR(100), deleted INT DEFAULT 0)");
            sql.execute("INSERT INTO canteen_stall (id, name, deleted) VALUES (1, '一食堂', 0)");
        }

        StallMapper stallMapper = mock(StallMapper.class);
        Stall stall = new Stall();
        stall.setId(1L);
        stall.setName("一食堂");
        when(stallMapper.selectById(anyLong())).thenReturn(stall);

        dishService = new DishServiceImpl(dishMapper, stallMapper,
                mock(DishReviewMapper.class), mock(StatsMapper.class), mock(CartItemMapper.class),
                notificationService, mock(com.qmz.canteen.mapper.MerchantMessageMapper.class));
        auditService = new DishAuditServiceImpl(dishMapper, notificationService);
    }

    @AfterEach
    void tearDown() {
        if (session != null) {
            session.close();
        }
    }

    @Test
    void merchantCreateStartsPendingAndOffShelf() {
        DishRequest request = request("宫保鸡丁", "12.00");
        request.setStatus(1);

        Long id = dishService.createDish(request, 1L);

        Dish saved = dishMapper.selectById(id);
        assertEquals(Dish.AUDIT_PENDING, saved.getAuditStatus());
        assertEquals(0, saved.getStatus());
        assertNull(saved.getAuditRemark());
    }

    @Test
    void adminCreateIsApprovedAndDefaultsOnShelf() {
        DishRequest request = request("麻婆豆腐", "8.00");
        request.setStatus(null);

        Long id = dishService.createDish(request, null);

        Dish saved = dishMapper.selectById(id);
        assertEquals(Dish.AUDIT_APPROVED, saved.getAuditStatus());
        assertEquals(1, saved.getStatus());
        assertNull(saved.getAuditRemark());
    }

    @Test
    void merchantRelistingRejectedDishGoesBackToPending() {
        Dish rejected = insertDish("回锅肉", Dish.AUDIT_REJECTED, 0);
        rejected.setAuditRemark("图片不清晰");
        dishMapper.updateById(rejected);

        DishRequest request = request("回锅肉", "18.00");
        request.setStatus(1);
        dishService.updateDish(rejected.getId(), request, 1L);

        Dish updated = dishMapper.selectById(rejected.getId());
        assertEquals(Dish.AUDIT_PENDING, updated.getAuditStatus());
        assertEquals(0, updated.getStatus());
        assertNull(updated.getAuditRemark());
    }

    @Test
    void editingApprovedDishDoesNotResetAudit() {
        Dish approved = insertDish("红烧肉", Dish.AUDIT_APPROVED, 1);

        DishRequest request = request("红烧肉", "20.00");
        request.setStatus(1);
        dishService.updateDish(approved.getId(), request, 1L);

        Dish updated = dishMapper.selectById(approved.getId());
        assertEquals(Dish.AUDIT_APPROVED, updated.getAuditStatus());
        assertEquals(1, updated.getStatus());
    }

    @Test
    void adminEditMarksDishApproved() {
        Dish rejected = insertDish("水煮肉片", Dish.AUDIT_REJECTED, 0);
        rejected.setAuditRemark("描述太简单");
        dishMapper.updateById(rejected);

        DishRequest request = request("水煮肉片", "16.00");
        request.setStatus(1);
        dishService.updateDish(rejected.getId(), request, null);

        Dish updated = dishMapper.selectById(rejected.getId());
        assertEquals(Dish.AUDIT_APPROVED, updated.getAuditStatus());
        assertEquals(1, updated.getStatus());
        assertNull(updated.getAuditRemark());
    }

    @Test
    void approveMovesPendingDishOnShelfAndNotifiesMerchant() {
        Dish pending = insertDish("鱼香肉丝", Dish.AUDIT_PENDING, 0);

        auditService.approve(pending.getId(), 99L);

        Dish updated = dishMapper.selectById(pending.getId());
        assertEquals(Dish.AUDIT_APPROVED, updated.getAuditStatus());
        assertEquals(1, updated.getStatus());
        assertEquals(99L, updated.getAuditBy());
        assertNotNull(updated.getAuditTime());
        assertNull(updated.getAuditRemark());
        verify(notificationService).notifyMerchant(eq(1L), eq(Notification.TYPE_ADMIN_NOTICE),
                contains("审核通过"), eq("/merchant/dishes"), eq(pending.getId()));
    }

    @Test
    void rejectKeepsDishOffShelfAndRecordsReasonAndNotifiesMerchant() {
        Dish pending = insertDish("辣子鸡", Dish.AUDIT_PENDING, 0);

        auditService.reject(pending.getId(), 99L, "  图片不清晰，请重新上传  ");

        Dish updated = dishMapper.selectById(pending.getId());
        assertEquals(Dish.AUDIT_REJECTED, updated.getAuditStatus());
        assertEquals(0, updated.getStatus());
        assertEquals("图片不清晰，请重新上传", updated.getAuditRemark());
        assertEquals(99L, updated.getAuditBy());
        assertNotNull(updated.getAuditTime());
        verify(notificationService).notifyMerchant(eq(1L), eq(Notification.TYPE_ADMIN_NOTICE),
                contains("审核未通过"), eq("/merchant/dishes"), eq(pending.getId()));
    }

    @Test
    void repeatedAuditReturnsConflictAndDoesNotNotifyAgain() {
        Dish pending = insertDish("宫保虾球", Dish.AUDIT_PENDING, 0);
        auditService.approve(pending.getId(), 99L);

        BusinessException conflict = assertThrows(BusinessException.class,
                () -> auditService.approve(pending.getId(), 100L));
        assertEquals(ResultCode.DATA_EXISTS.getCode(), conflict.getCode());

        BusinessException rejectConflict = assertThrows(BusinessException.class,
                () -> auditService.reject(pending.getId(), 100L, "再驳一次"));
        assertEquals(ResultCode.DATA_EXISTS.getCode(), rejectConflict.getCode());

        verify(notificationService, times(1)).notifyMerchant(anyLong(), anyString(),
                anyString(), anyString(), anyLong());
    }

    @Test
    void auditMissingDishReturnsNotFound() {
        BusinessException missing = assertThrows(BusinessException.class,
                () -> auditService.approve(9999L, 99L));
        assertEquals(ResultCode.NOT_FOUND.getCode(), missing.getCode());
        verifyNoInteractions(notificationService);
    }

    @Test
    void auditPageFiltersByStatusAndJoinsStallName() {
        insertDish("待审菜", Dish.AUDIT_PENDING, 0);
        insertDish("已通过菜", Dish.AUDIT_APPROVED, 1);

        IPage<DishAuditVO> page = dishMapper.selectAuditPage(new Page<>(1, 10), Dish.AUDIT_PENDING);

        assertEquals(1, page.getTotal());
        assertEquals("待审菜", page.getRecords().get(0).getName());
        assertEquals("一食堂", page.getRecords().get(0).getStallName());
        assertEquals(Dish.AUDIT_PENDING, page.getRecords().get(0).getAuditStatus());
    }

    private Dish insertDish(String name, String auditStatus, int status) {
        Dish dish = new Dish();
        dish.setStallId(1L);
        dish.setName(name);
        dish.setPrice(new BigDecimal("10.00"));
        dish.setCategory("荤菜");
        dish.setDescription("测试菜品");
        dish.setFeatured(0);
        dish.setStatus(status);
        dish.setSoldOut(0);
        dish.setDeleted(0);
        dish.setAuditStatus(auditStatus);
        dishMapper.insert(dish);
        return dish;
    }

    private DishRequest request(String name, String price) {
        DishRequest request = new DishRequest();
        request.setStallId(1L);
        request.setName(name);
        request.setPrice(new BigDecimal(price));
        request.setCategory("荤菜");
        request.setImage("/dishes/test.jpg");
        request.setDescription("测试菜品");
        request.setFeatured(0);
        request.setStatus(1);
        return request;
    }
}
