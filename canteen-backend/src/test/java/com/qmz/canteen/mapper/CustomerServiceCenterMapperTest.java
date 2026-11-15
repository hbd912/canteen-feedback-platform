package com.qmz.canteen.mapper;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisSqlSessionFactoryBuilder;
import com.baomidou.mybatisplus.autoconfigure.MybatisPlusProperties;
import com.qmz.canteen.dto.CustomerServiceRecordQuery;
import com.qmz.canteen.dto.KnowledgeQuery;
import com.qmz.canteen.vo.CustomerServiceRecordVO;
import com.qmz.canteen.vo.KnowledgeItemVO;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** 独立内存数据库测试，不加载应用配置、不连接业务数据库、不启动服务。 */
class CustomerServiceCenterMapperTest {

    private SqlSession session;
    private CustomerServiceCenterMapper mapper;
    private final LocalDateTime base = LocalDateTime.of(2026, 11, 14, 12, 0);

    @BeforeEach
    void setUp() throws Exception {
        UnpooledDataSource dataSource = new UnpooledDataSource("org.h2.Driver",
                "jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL", "sa", "");
        MybatisConfiguration configuration = new MybatisConfiguration();
        configuration.setEnvironment(new Environment("test", new JdbcTransactionFactory(), dataSource));
        String resource = "mapper/CustomerServiceCenterMapper.xml";
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(resource)) {
            assertNotNull(input);
            new XMLMapperBuilder(input, configuration, resource, configuration.getSqlFragments()).parse();
        }
        session = new MybatisSqlSessionFactoryBuilder().build(configuration).openSession();
        mapper = session.getMapper(CustomerServiceCenterMapper.class);
        try (Statement sql = session.getConnection().createStatement()) {
            sql.execute("CREATE TABLE sys_faq (id BIGINT PRIMARY KEY, question VARCHAR(1000), "
                    + "answer VARCHAR(2000), category VARCHAR(30), keywords VARCHAR(200), sort_no INT, "
                    + "status INT, create_time TIMESTAMP, deleted INT DEFAULT 0)");
            sql.execute("CREATE TABLE ai_experience (id BIGINT PRIMARY KEY, problem VARCHAR(1000), "
                    + "solution VARCHAR(2000), scene VARCHAR(60), tags VARCHAR(200), score INT, stall_id BIGINT, "
                    + "source VARCHAR(20), embedding VARCHAR(200), create_time TIMESTAMP, deleted INT DEFAULT 0)");
            sql.execute("CREATE TABLE sys_user (id BIGINT PRIMARY KEY, real_name VARCHAR(50), deleted INT DEFAULT 0)");
            sql.execute("CREATE TABLE canteen_stall (id BIGINT PRIMARY KEY, name VARCHAR(50), deleted INT DEFAULT 0)");
            sql.execute("CREATE TABLE chat_log (id BIGINT PRIMARY KEY, user_id BIGINT, question VARCHAR(500), "
                    + "hit INT, helpful INT, cost_ms INT, intent_label VARCHAR(50), create_time TIMESTAMP)");
            sql.execute("CREATE TABLE customer_service_session (id BIGINT PRIMARY KEY, user_id BIGINT, stall_id BIGINT, "
                    + "last_message VARCHAR(500), status VARCHAR(20), merchant_unread INT, order_id BIGINT, "
                    + "last_message_time TIMESTAMP, update_time TIMESTAMP, create_time TIMESTAMP, deleted INT DEFAULT 0)");
            sql.execute("INSERT INTO sys_user VALUES (1, '学生甲', 0), (2, '学生乙', 0)");
            sql.execute("INSERT INTO canteen_stall VALUES (1, '第一档口', 0)");
        }
    }

    @AfterEach
    void tearDown() {
        if (session != null) {
            session.close();
        }
    }

    @Test
    void mapperXmlIsCoveredByExistingDefaultResourceScan() {
        assertTrue(List.of(new MybatisPlusProperties().resolveMapperLocations()).stream()
                .anyMatch(resource -> "CustomerServiceCenterMapper.xml".equals(resource.getFilename())));
    }

    @Test
    void knowledgeFiltersWholeUnionAndPaginatesBeyondFirstHundred() throws Exception {
        seedKnowledge();
        KnowledgeQuery query = new KnowledgeQuery();
        assertEquals(240, mapper.countKnowledge(query));
        List<KnowledgeItemVO> first = mapper.selectKnowledge(query, 0);
        assertEquals("EXPERIENCE", first.get(0).getKind());
        assertEquals(120L, first.get(0).getId());
        assertEquals("问题120", first.get(0).getQuestion());
        assertEquals("解法120", first.get(0).getAnswer());
        assertEquals("FAQ", first.get(1).getKind());
        assertEquals(120L, first.get(1).getId());
        assertNull(first.get(1).getScene());
        assertNull(first.get(1).getHasEmbedding());
        List<KnowledgeItemVO> deepPage = mapper.selectKnowledge(query, 220);
        assertEquals(10, deepPage.size());
        assertEquals(10L, deepPage.get(0).getId());
        assertEquals("EXPERIENCE", deepPage.get(0).getKind());
        assertEquals(6L, deepPage.get(9).getId());

        query.setKeyword("needle");
        assertEquals(4, mapper.countKnowledge(query));
        List<KnowledgeItemVO> filtered = mapper.selectKnowledge(query, 0);
        assertEquals(List.of(4L, 3L, 2L, 1L), filtered.stream().map(KnowledgeItemVO::getId).toList());
        KnowledgeItemVO experience = filtered.get(1);
        assertEquals("SERVICE", experience.getScene());
        assertEquals("needle", experience.getTags());
        assertEquals(5, experience.getScore());
        assertEquals(1L, experience.getStallId());
        assertEquals("ADMIN", experience.getSource());
        assertTrue(experience.getHasEmbedding());
        assertNull(experience.getStatus());
        KnowledgeItemVO faq = filtered.get(0);
        assertEquals("OTHER", faq.getCategory());
        assertEquals("needle", faq.getKeywords());
        assertEquals(4, faq.getSortNo());
        assertEquals(1, faq.getStatus());
        assertNull(faq.getTags());

        query.setSize(2);
        assertEquals(List.of(2L, 1L), mapper.selectKnowledge(query, 2).stream()
                .map(KnowledgeItemVO::getId).toList());
        query.setKind("FAQ");
        assertEquals(2, mapper.countKnowledge(query));
        query.setKind("EXPERIENCE");
        assertEquals(2, mapper.countKnowledge(query));
        query.setKind("");
        query.setKeyword(null);
        query.setCategory("OTHER");
        assertEquals(120, mapper.countKnowledge(query));
        assertTrue(mapper.selectKnowledge(query, 0).stream().allMatch(row -> "FAQ".equals(row.getKind())));
        query.setScene("SERVICE");
        assertEquals(0, mapper.countKnowledge(query));
        query.setCategory(null);
        assertEquals(120, mapper.countKnowledge(query));
        assertTrue(mapper.selectKnowledge(query, 0).stream().allMatch(row -> "EXPERIENCE".equals(row.getKind())));
    }

    @Test
    void recordsUseGlobalPagingAttentionAndSessionTimeFallback() throws Exception {
        seedRecords();
        CustomerServiceRecordQuery query = new CustomerServiceRecordQuery();
        assertEquals(240, mapper.countRecords(query));
        List<CustomerServiceRecordVO> deepPage = mapper.selectRecords(query, 220);
        assertEquals(10, deepPage.size());
        assertEquals("SESSION", deepPage.get(0).getKind());
        assertEquals(10L, deepPage.get(0).getId());
        assertEquals("CHAT", deepPage.get(9).getKind());
        assertEquals(6L, deepPage.get(9).getId());

        query.setState("ATTENTION");
        assertEquals(3, mapper.countRecords(query));
        List<CustomerServiceRecordVO> attention = mapper.selectRecords(query, 0);
        assertEquals(List.of("SESSION", "CHAT", "CHAT"), attention.stream()
                .map(CustomerServiceRecordVO::getKind).toList());
        assertEquals(List.of("OPEN", "UNHELPFUL", "MISS"), attention.stream()
                .map(CustomerServiceRecordVO::getStatus).toList());
        CustomerServiceRecordVO conversation = attention.get(0);
        assertEquals("会话3", conversation.getQuestion());
        assertEquals("学生乙", conversation.getPerson());
        assertEquals("第一档口", conversation.getStallName());
        assertEquals(2, conversation.getMerchantUnread());
        assertEquals(42L, conversation.getOrderId());
        assertEquals(base.plusSeconds(7), conversation.getCreateTime());
        assertNull(conversation.getHit());
        CustomerServiceRecordVO chat = attention.get(1);
        assertEquals(1, chat.getHit());
        assertEquals(0, chat.getHelpful());
        assertEquals(15, chat.getCostMs());
        assertEquals("咨询", chat.getIntentLabel());
        assertNull(chat.getStallName());

        query.setKind("CHAT");
        assertEquals(2, mapper.countRecords(query));
        query.setKind("SESSION");
        assertEquals(1, mapper.countRecords(query));
        query.setState(null);
        query.setKind("");
        query.setKeyword("学生甲");
        assertEquals(120, mapper.countRecords(query));
        query.setKeyword("第一档口");
        assertEquals(120, mapper.countRecords(query));
        query.setKeyword("提问1");
        assertEquals(32, mapper.countRecords(query));
        query.setKeyword("会话2");
        query.setKind("SESSION");
        query.setSize(100);
        List<CustomerServiceRecordVO> fallbackRows = mapper.selectRecords(query, 0);
        CustomerServiceRecordVO updateFallback = fallbackRows.stream().filter(row -> row.getId() == 2L)
                .findFirst().orElseThrow();
        assertEquals(base.plusSeconds(5), updateFallback.getCreateTime());
        query.setKeyword("会话1");
        query.setSize(100);
        CustomerServiceRecordVO createFallback = mapper.selectRecords(query, 0).stream()
                .filter(row -> row.getId() == 1L).findFirst().orElseThrow();
        assertEquals(base.plusSeconds(3), createFallback.getCreateTime());
    }

    @Test
    void equalTimesSortByKindThenDescendingId() throws Exception {
        seedKnowledge();
        seedRecords();
        try (Statement sql = session.getConnection().createStatement()) {
            sql.execute("UPDATE sys_faq SET create_time = '2026-11-14 12:00:00'");
            sql.execute("UPDATE ai_experience SET create_time = '2026-11-14 12:00:00'");
            sql.execute("UPDATE chat_log SET create_time = '2026-11-14 12:00:00'");
            sql.execute("UPDATE customer_service_session SET last_message_time = '2026-11-14 12:00:00'");
        }
        List<KnowledgeItemVO> knowledge = mapper.selectKnowledge(new KnowledgeQuery(), 118);
        assertEquals(List.of("EXPERIENCE", "EXPERIENCE", "FAQ", "FAQ"), knowledge.subList(0, 4)
                .stream().map(KnowledgeItemVO::getKind).toList());
        assertEquals(List.of(2L, 1L, 120L, 119L), knowledge.subList(0, 4)
                .stream().map(KnowledgeItemVO::getId).toList());
        List<CustomerServiceRecordVO> records = mapper.selectRecords(new CustomerServiceRecordQuery(), 118);
        assertEquals(List.of("CHAT", "CHAT", "SESSION", "SESSION"), records.subList(0, 4)
                .stream().map(CustomerServiceRecordVO::getKind).toList());
        assertEquals(List.of(2L, 1L, 120L, 119L), records.subList(0, 4)
                .stream().map(CustomerServiceRecordVO::getId).toList());
    }

    private void seedKnowledge() throws Exception {
        try (PreparedStatement faq = session.getConnection().prepareStatement(
                "INSERT INTO sys_faq (id, question, answer, category, sort_no, status, create_time) "
                        + "VALUES (?, ?, ?, 'OTHER', ?, 1, ?)");
             PreparedStatement experience = session.getConnection().prepareStatement(
                     "INSERT INTO ai_experience (id, problem, solution, scene, score, stall_id, source, create_time) "
                             + "VALUES (?, ?, ?, 'SERVICE', 5, 1, 'ADMIN', ?)")) {
            for (int i = 1; i <= 120; i++) {
                faq.setLong(1, i);
                faq.setString(2, "问题" + i);
                faq.setString(3, i == 1 ? "needle" : "答案" + i);
                faq.setInt(4, i);
                faq.setTimestamp(5, Timestamp.valueOf(base.plusSeconds(i * 2L)));
                faq.addBatch();
                experience.setLong(1, i);
                experience.setString(2, "问题" + i);
                experience.setString(3, i == 2 ? "needle" : "解法" + i);
                experience.setTimestamp(4, Timestamp.valueOf(base.plusSeconds(i * 2L + 1)));
                experience.addBatch();
            }
            faq.executeBatch();
            experience.executeBatch();
        }
        try (Statement sql = session.getConnection().createStatement()) {
            sql.execute("UPDATE sys_faq SET keywords = 'needle' WHERE id = 4");
            sql.execute("UPDATE ai_experience SET tags = 'needle', embedding = '[1,2]' WHERE id = 3");
            sql.execute("INSERT INTO sys_faq (id, question, deleted) VALUES (999, 'needle', 1)");
            sql.execute("INSERT INTO ai_experience (id, problem, deleted) VALUES (999, 'needle', 1)");
        }
    }

    private void seedRecords() throws Exception {
        try (PreparedStatement chat = session.getConnection().prepareStatement(
                "INSERT INTO chat_log VALUES (?, 1, ?, ?, ?, 15, '咨询', ?)");
             PreparedStatement conversation = session.getConnection().prepareStatement(
                     "INSERT INTO customer_service_session "
                             + "(id, user_id, stall_id, last_message, status, merchant_unread, order_id, "
                             + "last_message_time, update_time, create_time) "
                             + "VALUES (?, 2, 1, ?, 'OPEN', ?, 42, ?, ?, ?)")) {
            for (int i = 1; i <= 120; i++) {
                chat.setLong(1, i);
                chat.setString(2, "提问" + i);
                chat.setInt(3, i == 1 ? 0 : 1);
                chat.setInt(4, i == 2 ? 0 : 1);
                chat.setTimestamp(5, Timestamp.valueOf(base.plusSeconds(i * 2L)));
                chat.addBatch();
                conversation.setLong(1, i);
                conversation.setString(2, "会话" + i);
                conversation.setInt(3, i == 3 ? 2 : 0);
                Timestamp time = Timestamp.valueOf(base.plusSeconds(i * 2L + 1));
                conversation.setTimestamp(4, i <= 2 ? null : time);
                conversation.setTimestamp(5, i == 1 ? null : time);
                conversation.setTimestamp(6, time);
                conversation.addBatch();
            }
            chat.executeBatch();
            conversation.executeBatch();
        }
        try (Statement sql = session.getConnection().createStatement()) {
            sql.execute("INSERT INTO customer_service_session (id, last_message, merchant_unread, deleted) "
                    + "VALUES (999, '会话', 3, 1)");
        }
    }
}
