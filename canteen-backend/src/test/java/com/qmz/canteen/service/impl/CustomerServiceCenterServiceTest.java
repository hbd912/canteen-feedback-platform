package com.qmz.canteen.service.impl;

import com.qmz.canteen.common.BusinessException;
import com.qmz.canteen.common.PageResult;
import com.qmz.canteen.common.ResultCode;
import com.qmz.canteen.dto.CustomerServiceRecordQuery;
import com.qmz.canteen.dto.KnowledgeQuery;
import com.qmz.canteen.mapper.CustomerServiceCenterMapper;
import com.qmz.canteen.security.LoginUser;
import com.qmz.canteen.security.UserContext;
import com.qmz.canteen.vo.KnowledgeItemVO;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CustomerServiceCenterServiceTest {

    @Mock
    private CustomerServiceCenterMapper mapper;
    @InjectMocks
    private CustomerServiceCenterServiceImpl service;

    @BeforeEach
    void setUp() {
        LoginUser admin = new LoginUser();
        admin.setRole("ADMIN");
        UserContext.set(admin);
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    void returnsStablePageMetadataAndUsesLongOffset() {
        KnowledgeQuery query = new KnowledgeQuery();
        query.setCurrent(23);
        when(mapper.countKnowledge(query)).thenReturn(241L);
        KnowledgeItemVO record = new KnowledgeItemVO();
        record.setId(1L);
        when(mapper.selectKnowledge(query, 220L)).thenReturn(List.of(record));
        PageResult<KnowledgeItemVO> page = service.knowledge(query);
        assertEquals(241L, page.getTotal());
        assertEquals(23L, page.getCurrent());
        assertEquals(10L, page.getSize());
        assertEquals(25L, page.getPages());
        assertEquals(List.of(record), page.getRecords());

        CustomerServiceRecordQuery records = new CustomerServiceRecordQuery();
        records.setCurrent(Integer.MAX_VALUE);
        records.setSize(100);
        when(mapper.countRecords(records)).thenReturn(1L);
        when(mapper.selectRecords(records, 214748364600L)).thenReturn(List.of());
        assertEquals(1L, service.records(records).getPages());
        verify(mapper).selectRecords(records, 214748364600L);
    }

    @Test
    void nonAdminIsRejectedBeforeEitherQuery() {
        UserContext.clear();
        assertEquals(ResultCode.FORBIDDEN.getCode(), assertThrows(BusinessException.class,
                () -> service.knowledge(new KnowledgeQuery())).getCode());
        assertEquals(ResultCode.FORBIDDEN.getCode(), assertThrows(BusinessException.class,
                () -> service.records(new CustomerServiceRecordQuery())).getCode());
        verifyNoInteractions(mapper);
    }

    @Test
    void invalidPagesDoNotReachMapper() {
        KnowledgeQuery knowledge = new KnowledgeQuery();
        knowledge.setCurrent(0);
        assertThrows(BusinessException.class, () -> service.knowledge(knowledge));
        CustomerServiceRecordQuery records = new CustomerServiceRecordQuery();
        records.setSize(101);
        assertThrows(BusinessException.class, () -> service.records(records));
        verifyNoInteractions(mapper);
    }

    @Test
    void requestValidationRejectsUnknownKindsAndStatesAndInvalidPageSizes() {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            KnowledgeQuery knowledge = new KnowledgeQuery();
            CustomerServiceRecordQuery records = new CustomerServiceRecordQuery();
            assertTrue(validator.validate(knowledge).isEmpty());
            assertTrue(validator.validate(records).isEmpty());
            knowledge.setKind("");
            records.setKind("");
            records.setState("");
            assertTrue(validator.validate(knowledge).isEmpty());
            assertTrue(validator.validate(records).isEmpty());
            knowledge.setKind("AUDIT");
            knowledge.setSize(0);
            records.setKind("FAQ");
            records.setState("OPEN");
            records.setCurrent(0);
            assertEquals(2, validator.validate(knowledge).size());
            assertEquals(3, validator.validate(records).size());
        }
    }
}
