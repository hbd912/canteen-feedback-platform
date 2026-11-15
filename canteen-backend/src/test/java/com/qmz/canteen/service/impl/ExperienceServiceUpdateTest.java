package com.qmz.canteen.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisSqlSessionFactoryBuilder;
import com.qmz.canteen.agent.EmbeddingClient;
import com.qmz.canteen.common.BusinessException;
import com.qmz.canteen.common.ResultCode;
import com.qmz.canteen.controller.AdminExperienceController;
import com.qmz.canteen.dto.ExperienceRequest;
import com.qmz.canteen.entity.AiExperience;
import com.qmz.canteen.mapper.AiExperienceHitMapper;
import com.qmz.canteen.mapper.AiExperienceMapper;
import com.qmz.canteen.security.UserContext;
import com.qmz.canteen.service.ExperienceService;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ExperienceServiceUpdateTest {

    private SqlSession session;
    private AiExperienceMapper mapper;
    private ExperienceServiceImpl service;
    private EmbeddingClient embeddingClient;
    private final LocalDateTime created = LocalDateTime.of(2026, 11, 14, 9, 0);

    @BeforeEach
    void setUp() throws Exception {
        MybatisConfiguration configuration = new MybatisConfiguration();
        configuration.setEnvironment(new Environment("test", new JdbcTransactionFactory(),
                new UnpooledDataSource("org.h2.Driver", "jdbc:h2:mem:" + UUID.randomUUID()
                        + ";MODE=MySQL", "sa", "")));
        configuration.addMapper(AiExperienceMapper.class);
        session = new MybatisSqlSessionFactoryBuilder().build(configuration).openSession();
        mapper = session.getMapper(AiExperienceMapper.class);
        embeddingClient = mock(EmbeddingClient.class);
        service = new ExperienceServiceImpl(mapper, mock(AiExperienceHitMapper.class), embeddingClient);
        try (Statement sql = session.getConnection().createStatement()) {
            sql.execute("CREATE TABLE ai_experience (id BIGINT PRIMARY KEY, scene VARCHAR(60), "
                    + "problem VARCHAR(1000), solution VARCHAR(2000), outcome VARCHAR(20), score INT, "
                    + "tags VARCHAR(200), stall_id BIGINT, embedding VARCHAR(200), embedding_model VARCHAR(100), "
                    + "source VARCHAR(20), ref_id BIGINT, create_time TIMESTAMP, deleted INT DEFAULT 0)");
        }
        AiExperience existing = new AiExperience();
        existing.setId(1L);
        existing.setScene("SERVICE");
        existing.setProblem("原问题");
        existing.setSolution("原解法");
        existing.setOutcome("CORRECTED");
        existing.setScore(5);
        existing.setTags("原标签");
        existing.setStallId(20L);
        existing.setEmbedding("[1,2]");
        existing.setEmbeddingModel("original-model");
        existing.setSource("MERCHANT");
        existing.setRefId(99L);
        existing.setCreateTime(created);
        existing.setDeleted(0);
        mapper.insert(existing);
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
        if (session != null) {
            session.close();
        }
    }

    @Test
    void editPreservesIdentityAndProvenanceAndExplicitlyClearsNullableFieldsAndVector() {
        ExperienceRequest request = request();
        request.setScene(" NEW_SCENE ");
        request.setProblem(" 新问题 ");
        request.setSolution(" 新解法 ");
        request.setTags(null);
        request.setScore(null);
        request.setStallId(null);
        service.update(1L, request);

        AiExperience updated = mapper.selectById(1L);
        assertEquals("NEW_SCENE", updated.getScene());
        assertEquals("新问题", updated.getProblem());
        assertEquals("新解法", updated.getSolution());
        assertNull(updated.getTags());
        assertNull(updated.getScore());
        assertNull(updated.getStallId());
        assertNull(updated.getEmbedding());
        assertNull(updated.getEmbeddingModel());
        assertEquals("MERCHANT", updated.getSource());
        assertEquals(99L, updated.getRefId());
        assertEquals("CORRECTED", updated.getOutcome());
        assertEquals(created, updated.getCreateTime());
        assertEquals(1, mapper.selectCount(null));
        assertEquals(1L, mapper.selectPendingEmbedding(50).get(0).getId());
        verifyNoInteractions(embeddingClient);
    }

    @Test
    void metadataOnlyEditKeepsUsableVector() {
        ExperienceRequest request = request();
        request.setScore(3);
        request.setStallId(null);
        service.update(1L, request);
        AiExperience updated = mapper.selectById(1L);
        assertEquals(3, updated.getScore());
        assertNull(updated.getStallId());
        assertEquals("[1,2]", updated.getEmbedding());
        assertEquals("original-model", updated.getEmbeddingModel());
        verifyNoInteractions(embeddingClient);
    }

    @Test
    void missingOrDeletedExperienceIsRejected() throws Exception {
        BusinessException missing = assertThrows(BusinessException.class, () -> service.update(2L, request()));
        assertEquals(ResultCode.NOT_FOUND.getCode(), missing.getCode());
        try (Statement sql = session.getConnection().createStatement()) {
            sql.execute("UPDATE ai_experience SET deleted = 1 WHERE id = 1");
        }
        BusinessException deleted = assertThrows(BusinessException.class, () -> service.update(1L, request()));
        assertEquals(ResultCode.NOT_FOUND.getCode(), deleted.getCode());
        verifyNoInteractions(embeddingClient);
    }

    @Test
    void editEndpointRequiresAdminBeforeCallingService() {
        ExperienceService mockService = mock(ExperienceService.class);
        AdminExperienceController controller = new AdminExperienceController(mockService);
        BusinessException forbidden = assertThrows(BusinessException.class, () -> controller.update(1L, request()));
        assertEquals(ResultCode.FORBIDDEN.getCode(), forbidden.getCode());
        verifyNoInteractions(mockService);
    }

    private ExperienceRequest request() {
        ExperienceRequest request = new ExperienceRequest();
        request.setScene("SERVICE");
        request.setProblem("原问题");
        request.setSolution("原解法");
        request.setTags("原标签");
        request.setStallId(20L);
        return request;
    }
}
